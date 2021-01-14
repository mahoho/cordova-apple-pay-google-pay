# Cordova Apple Pay and Google Pay integration

This plugin is built as unified method for obtaining payment tokens to forward it to payment processor (eg Stripe, Wayforpay, etc).

Plugin supports iOS 11-14. Tested properly with cordova 10 and iOS 14.3.

## Installation

```
cordova plugin add cordova-plugin-apple-pay-google-pay
```

## Usage

`canMakePayments()` checks whether device is capable to make payments via Apple Pay or Google Pay.  
```
// use as plain Promise
async function checkForApplePayOrGooglePay(){
    let isAvailable = await cordova.plugins.ApplePayGooglePay.canMakePayments()
}

// OR
let available;

cordova.plugins.ApplePayGooglePay.canMakePayments((r) => {
  available = r
})
```
`makePaymentRequest()` initiates pay session.

```
cordova.plugins.ApplePayGooglePay.makePaymentRequest({
        merchantId: 'merchant.com.example', // obtain it from https://developer.apple.com/account/resources/identifiers/list/merchant
        purpose: `Payment for your order #1`,
        amount: 100,
        countryCode: "US",
        currencyCode: "USD"
      }, r => {
        // in success callback, raw response as encoded JSON is returned. Pass it to your payment processor as is. 
        let responseString = r
    
      },
        r => {
          // in error callback, error message is returned.
          // it will be "Payment cancelled" if used pressed Cancel button.
        }
      )
```

All parameters in request object are required.
