package com.plugin.googlepay;

import android.app.Activity;
import android.content.Intent;
import androidx.annotation.NonNull;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wallet.*;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

//import java.util.concurrent.Executor;

/**
 * Google Pay implementation for Cordova
 */
public class ApplePayGooglePay extends CordovaPlugin {
    private static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 991;
    private PaymentsClient paymentsClient;

    private CallbackContext callbackContext;

    private JSONArray allowedCardAuthMethods = new JSONArray(
            Arrays.asList(
                    "CRYPTOGRAM_3DS",
                    "PAN_ONLY"
            )
    );

    private JSONArray allowedCardNetworks = new JSONArray(
            Arrays.asList(
                    "AMEX",
                    "DISCOVER",
                    "JCB",
                    "MASTERCARD",
                    "VISA"
            )
    );

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Wallet.WalletOptions walletOptions = new Wallet.WalletOptions.Builder().setEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION).build();
        Activity activity = cordova.getActivity();

        paymentsClient = Wallet.getPaymentsClient(activity, walletOptions);

        if (action.equals("canMakePayments")) {
            this.canMakePayments(args, callbackContext);
            return true;
        }
        if (action.equals("makePaymentRequest")) {
            this.makePaymentRequest(args, callbackContext);
            return true;
        }
        return false;
    }

    /**
     * Handle a resolved activity from the Google Pay payment sheet.
     *
     * @param requestCode Request code originally supplied to AutoResolveHelper in requestPayment().
     * @param resultCode  Result code returned by the Google Pay API.
     * @param data        Intent from the Google Pay API containing payment or error data.
     * @see <a href="https://developer.android.com/training/basics/intents/result">Getting a result
     * from an Activity</a>
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // value passed in AutoResolveHelper
        if (requestCode != LOAD_PAYMENT_DATA_REQUEST_CODE) {
            return;
        }

        switch (resultCode) {

            case Activity.RESULT_OK:
                PaymentData paymentData = PaymentData.getFromIntent(data);
                String paymentInfo = paymentData.toJson();
                callbackContext.success(paymentInfo);
                break;

            case Activity.RESULT_CANCELED:
                callbackContext.error("Payment cancelled");
                break;

            case AutoResolveHelper.RESULT_ERROR:
                Status status = AutoResolveHelper.getStatusFromIntent(data);
                callbackContext.error(status.getStatusMessage());
                break;
        }
    }

    private void canMakePayments(JSONArray args, CallbackContext callbackContext) throws JSONException {

        // The call to isReadyToPay is asynchronous and returns a Task. We need to provide an
        // OnCompleteListener to be triggered when the result of the call is known.
        IsReadyToPayRequest request = IsReadyToPayRequest.newBuilder()
                .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_TOKENIZED_CARD)
                .build();

        Task<Boolean> task = paymentsClient.isReadyToPay(request);

        task.addOnCompleteListener(cordova.getActivity(),
                new OnCompleteListener<Boolean>() {
                    @Override
                    public void onComplete(@NonNull Task<Boolean> task) {
                        boolean result = task.isSuccessful();

                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
                    }
                });

    }

    private void makePaymentRequest(JSONArray args, CallbackContext callbackContext) throws JSONException {
        JSONObject argss = args.getJSONObject(0);
        Activity activity = cordova.getActivity();
        cordova.setActivityResultCallback(this);

        this.callbackContext = callbackContext;

        try {
            String price = getParam(argss, "amount");
            String currencyCode = getParam(argss, "currencyCode");
            String countryCode = getParam(argss, "countryCode");

            String gateway = getParam(argss, "gateway");
            String merchantId = getParam(argss, "merchantId");
            String gpMerchantId = getParam(argss, "gpMerchantId");
            String gpMerchantName = getParam(argss, "gpMerchantName");

            JSONObject paymentDataRequest = getBaseRequest();
            paymentDataRequest.put(
                    "allowedPaymentMethods", new JSONArray().put(getCardPaymentMethod(gateway, merchantId)));
            paymentDataRequest.put("transactionInfo", getTransactionInfo(price, currencyCode, countryCode));
            paymentDataRequest.put("merchantInfo",
                    new JSONObject()
                            .put("merchantName", gpMerchantName)
                            .put("merchantId", gpMerchantId)
            );


            String requestJson = paymentDataRequest.toString();

            PaymentDataRequest request = PaymentDataRequest.fromJson(requestJson);

            // Since loadPaymentData may show the UI asking the user to select a payment method, we use
            // AutoResolveHelper to wait for the user interacting with it. Once completed,
            // onActivityResult will be called with the result.
            if (request != null) {
                AutoResolveHelper.resolveTask(paymentsClient.loadPaymentData(request), activity, LOAD_PAYMENT_DATA_REQUEST_CODE);
            }

        } catch (JSONException e) {
            callbackContext.error(e.getMessage());
        }
    }


    /**
     * Gateway Integration: Identify your gateway and your app's gateway merchant identifier.
     *
     * <p>The Google Pay API response will return an encrypted payment method capable of being charged
     * by a supported gateway after payer authorization.
     *
     * <p>TODO: Check with your gateway on the parameters to pass and modify them in Constants.java.
     *
     * @return Payment data tokenization for the CARD payment method.
     * @throws JSONException
     * @see <a href=
     * "https://developers.google.com/pay/api/android/reference/object#PaymentMethodTokenizationSpecification">PaymentMethodTokenizationSpecification</a>
     */
    private static JSONObject getGatewayTokenizationSpecification(String gateway, String gatewayMerchantId) throws JSONException {
        return new JSONObject() {{
            put("type", "PAYMENT_GATEWAY");
            put("parameters", new JSONObject() {{
                put("gateway", gateway);
                put("gatewayMerchantId", gatewayMerchantId);
            }});
        }};
    }


    /**
     * Describe your app's support for the CARD payment method.
     *
     * <p>The provided properties are applicable to both an IsReadyToPayRequest and a
     * PaymentDataRequest.
     *
     * @return A CARD PaymentMethod object describing accepted cards.
     * @throws JSONException
     * @see <a
     * href="https://developers.google.com/pay/api/android/reference/object#PaymentMethod">PaymentMethod</a>
     */
    private JSONObject getBaseCardPaymentMethod() throws JSONException {
        JSONObject cardPaymentMethod = new JSONObject();
        cardPaymentMethod.put("type", "CARD");

        JSONObject parameters = new JSONObject();
        parameters.put("allowedAuthMethods", allowedCardAuthMethods);
        parameters.put("allowedCardNetworks", allowedCardNetworks);
        cardPaymentMethod.put("parameters", parameters);

        return cardPaymentMethod;
    }

    /**
     * Describe the expected returned payment data for the CARD payment method
     *
     * @return A CARD PaymentMethod describing accepted cards and optional fields.
     * @throws JSONException
     * @see <a
     * href="https://developers.google.com/pay/api/android/reference/object#PaymentMethod">PaymentMethod</a>
     */
    private JSONObject getCardPaymentMethod(String gateway, String gatewayMerchantId) throws JSONException {
        JSONObject cardPaymentMethod = getBaseCardPaymentMethod();
        cardPaymentMethod.put("tokenizationSpecification", getGatewayTokenizationSpecification(gateway, gatewayMerchantId));

        return cardPaymentMethod;
    }

    private static JSONObject getBaseRequest() throws JSONException {
        return new JSONObject().put("apiVersion", 2).put("apiVersionMinor", 0);
    }

    /**
     * Provide Google Pay API with a payment amount, currency, and amount status.
     *
     * @return information about the requested payment.
     * @throws JSONException
     * @see <a
     * href="https://developers.google.com/pay/api/android/reference/object#TransactionInfo">TransactionInfo</a>
     */
    private JSONObject getTransactionInfo(String price, String currencyCode, String countryCode) throws JSONException {
        JSONObject transactionInfo = new JSONObject();
        transactionInfo.put("totalPrice", price);
        transactionInfo.put("totalPriceStatus", "FINAL");
        transactionInfo.put("countryCode", countryCode);
        transactionInfo.put("currencyCode", currencyCode);
        transactionInfo.put("checkoutOption", "COMPLETE_IMMEDIATE_PURCHASE");

        return transactionInfo;
    }


    private String getParam(JSONObject args, String name) throws JSONException {
        String param = args.getString(name);

        if (param == null || param.length() == 0) {
            throw new JSONException(String.format("%s is required", name));
        }

        return param;
    }
}
