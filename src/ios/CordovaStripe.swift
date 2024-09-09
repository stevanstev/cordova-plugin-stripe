import Foundation

@objc(CordovaStripe) class CordovaStripe: CDVPlugin {
    
    let stripeApiUrl = "https://api.stripe.com/v1"
    
    @objc(createPaymentSession:)
    func createPaymentSession(command: CDVInvokedUrlCommand) {
        let secretKey = command.argument(at: 0) as? String ?? ""
        let mode = command.argument(at: 1) as? String ?? "payment"
        let currency = command.argument(at: 2) as? String ?? ""
        // convert amount to be int type
        let amount = command.argument(at: 3) as? Int ?? 0
        let paymentSuccessUrl = command.argument(at: 4) as? String ?? ""
        let paymentCancelUrl = command.argument(at: 5) as? String
        let priceId = command.argument(at: 6) as? String
        let customerId = command.argument(at: 7) as? String

        guard !paymentSuccessUrl.isEmpty else {
            let errorResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Please specify payment success URL")
            self.commandDelegate.send(errorResult, callbackId: command.callbackId)
            return
        }
        
        if mode.lowercased() == "subscription" {
            createSubscriptionLink(secretKey: secretKey, priceId: priceId, customerID: customerId, paymentSuccessUrl: paymentSuccessUrl, paymentCancelUrl: paymentCancelUrl, callbackId: command.callbackId)
        } else {
            createPaymentLink(secretKey: secretKey, amount: amount, currency: currency, paymentSuccessUrl: paymentSuccessUrl, paymentCancelUrl: paymentCancelUrl, callbackId: command.callbackId)
        }
    }
    
    private func createPaymentLink(secretKey: String, amount: Int, currency: String, paymentSuccessUrl: String, paymentCancelUrl: String?, callbackId: String) {
        let productName = "Stripe Product Checkout"
        
        // URL encode the success and cancel URLs
        let encodedSuccessUrl = paymentSuccessUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? paymentSuccessUrl
        let encodedCancelUrl = paymentCancelUrl?.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)

        var requestBody = "line_items[0][price_data][unit_amount]=\(amount)&line_items[0][price_data][currency]=\(currency)&line_items[0][price_data][product_data][name]=\(productName)&line_items[0][quantity]=1&mode=payment&success_url=\(encodedSuccessUrl)"
        
        if let encodedCancelUrl = encodedCancelUrl {
            requestBody += "&cancel_url=\(encodedCancelUrl)"
        }

        apiCall(secretKey: secretKey, path: "/checkout/sessions", method: "POST", body: requestBody) { response, error in
            guard let response = response, let paymentLink = response["url"] as? String else {
                self.sendErrorResult(error ?? "Failed to create payment link", callbackId: callbackId)
                return
            }
            self.sendSuccessResult(paymentLink, callbackId: callbackId)
        }
    }

    
    private func createSubscriptionLink(secretKey: String, priceId: String?, customerID: String?, paymentSuccessUrl: String, paymentCancelUrl: String?, callbackId: String) {
        var requestBody = "line_items[0][price]=\(priceId ?? "")&line_items[0][quantity]=1&mode=subscription&success_url=\(paymentSuccessUrl)&customer=\(customerID ?? "")"
        if let paymentCancelUrl = paymentCancelUrl {
            requestBody += "&cancel_url=\(paymentCancelUrl)"
        }
        apiCall(secretKey: secretKey, path: "/checkout/sessions", method: "POST", body: requestBody) { response, error in
            guard let response = response, let paymentLink = response["url"] as? String else {
                self.sendErrorResult(error ?? "Failed to create subscription link", callbackId: callbackId)
                return
            }
            self.sendSuccessResult(paymentLink, callbackId: callbackId)
        }
    }
    
    private func apiCall(secretKey: String, path: String, method: String, body: String, completion: @escaping ([String: Any]?, String?) -> Void) {
        guard let url = URL(string: "\(stripeApiUrl)\(path)") else {
            completion(nil, "Invalid URL")
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("Bearer \(secretKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = body.data(using: .utf8)
        
        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            guard error == nil else {
                completion(nil, error?.localizedDescription)
                return
            }
            
            guard let data = data else {
                completion(nil, "No data received")
                return
            }

            do {
                if let json = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] {
                    if let errorJson = json["error"] as? [String: Any], let message = errorJson["message"] as? String {
                        completion(nil, message)
                    } else {
                        completion(json, nil)
                    }
                } else {
                    completion(nil, "Failed to parse JSON")
                }
            } catch {
                completion(nil, "JSON parsing error: \(error.localizedDescription)")
            }
        }
        task.resume()
    }
    
    private func sendSuccessResult(_ message: String, callbackId: String) {
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message)
        self.commandDelegate.send(pluginResult, callbackId: callbackId)
    }
    
    private func sendErrorResult(_ message: String, callbackId: String) {
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: message)
        self.commandDelegate.send(pluginResult, callbackId: callbackId)
    }
}
