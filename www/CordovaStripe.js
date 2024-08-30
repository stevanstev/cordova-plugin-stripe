
var exec = require('cordova/exec');
// Library from cordova where we can validate arg that sent
var argscheck = require('cordova/argscheck');
var getValue = argscheck.getValue;

// @desc Main Object of Function(s)
var stripeModule = {};

/*
    @name Create Payment Session
    @desc Create new object of one time payment session
*/
stripeModule.createPaymentSession = (successCallback, errorCallback, options) => {
    options = options || {};

    var args = [];

    args.push(getValue(options.secretKey, null));
    args.push(getValue(options.mode, null));
    args.push(getValue(options.currency, null));
    args.push(getValue(options.amount, null));
    args.push(getValue(options.paymentSuccessUrl, null));
    
    exec(successCallback, errorCallback, 'CordovaStripe', 'createPaymentSession', args);
}

module.exports = stripeModule;