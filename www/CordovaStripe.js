
var exec = require('cordova/exec');
// Library from cordova where we can validate arg that sent
var argscheck = require('cordova/argscheck');
var getValue = argscheck.getValue;

// Main Object of Function(s)
var stripeModule = {};

// Create new object of one time payment session
stripeModule.createPaymentSession = (successCallback, errorCallback, options) => {
    options = options || {};

    var args = [];

    args.push(getValue(options.secretKey, null));
    args.push(getValue(options.mode, 'payment'));
    args.push(getValue(options.currency, null));
    args.push(getValue(options.amount, null));
    args.push(getValue(options.paymentSuccessUrl, null));
    args.push(getValue(options.paymentCancelUrl, null));
    // only for subscribe mode
    args.push(getValue(options.priceID, null)); 
    args.push(getValue(options.customerID, null));
    
    exec(successCallback, errorCallback, 'CordovaStripe', 'createPaymentSession', args);
}

stripeModule.openInAppWebPage = (url) =>  {
    var ref = cordova.InAppBrowser.open(url, '_blank', 'location=yes');
    ref.addEventListener('loadstart', function() { console.log('Loading started'); });
    ref.addEventListener('loadstop', function() { console.log('Loading finished'); });
    ref.addEventListener('exit', function() { console.log('Browser closed'); });
}

stripeModule.openExternalWebPage = (url) =>  {
    var ref = window.open(url, '_blank', 'location=yes');
    ref.addEventListener('loadstart', function() { console.log('Loading started'); });
    ref.addEventListener('loadstop', function() { console.log('Loading finished'); });
    ref.addEventListener('exit', function() { console.log('Browser closed'); });
}

module.exports = stripeModule;