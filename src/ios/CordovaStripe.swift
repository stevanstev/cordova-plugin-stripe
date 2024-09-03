import Foundation

@objc(CordovaStripe) class CordovaStripe: CDVPlugin {
    
    let stripeApiUrl = "https://api.stripe.com/v1"
    
    @objc(createPaymentSession:)
    func createPaymentSession(command: CDVInvokedUrlCommand) {
        let secretKey = command.argument(at: 0) as? String ?? ""
        let mode = command.argument(at: 0) as? String
        let currency = command.argument(at: 2) as? String ?? ""
        let amount = command.argument(at: 3) as? Int ?? 0
        let paymentSuccessUrl = command.argument(at: 4) as? String ?? ""
        let paymentCancelUrl = command.argument(at: 5) as? String
        let priceId = command.argument(at: 6) as? String
        let customerId = command.argument(at: 7) as? String
        let itemQuantity = command.argument(at: 8) as? Int ?? 1

        guard !paymentSuccessUrl.isEmpty else {
            let errorResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Please specify payment success URL")
            self.commandDelegate.send(errorResult, callbackId: command.callbackId)
            return
        }
        
        if mode == "subscription" {
            createSubscriptionLink(secretKey: secretKey, priceId: priceId, customerID: customerId, itemQuantity: itemQuantity, paymentSuccessUrl: paymentSuccessUrl, paymentCancelUrl: paymentCancelUrl, callbackId: command.callbackId)
        } else {
            createProduct(secretKey: secretKey, productName: "PayProduct", currency: currency, amount: amount, paymentSuccessUrl: paymentSuccessUrl, paymentCancelUrl: paymentCancelUrl, callbackId: command.callbackId)
        }
    }
    
    private func createProduct(secretKey: String, productName: String, currency: String, amount: Int, paymentSuccessUrl: String, paymentCancelUrl: String?, callbackId: String) {
        let requestBody = "name=\(productName)"
        apiCall(secretKey: secretKey, path: "/products", method: "POST", body: requestBody) { response, error in
            guard let response = response, let productId = response["id"] as? String else {
                self.sendErrorResult(error ?? "Failed to create product", callbackId: callbackId)
                return
            }
            self.createPrice(secretKey: secretKey, currency: currency, amount: amount, productId: productId, paymentSuccessUrl: paymentSuccessUrl, paymentCancelUrl: paymentCancelUrl, callbackId: callbackId)
        }
    }
    
    private func createPrice(secretKey: String, currency: String, amount: Int, productId: String, paymentSuccessUrl: String, paymentCancelUrl: String?, callbackId: String) {
        let requestBody = "unit_amount=\(amount)&currency=\(currency)&product=\(productId)"
        apiCall(secretKey: secretKey, path: "/prices", method: "POST", body: requestBody) { response, error in
            guard let response = response, let priceId = response["id"] as? String else {
                self.sendErrorResult(error ?? "Failed to create price", callbackId: callbackId)
                return
            }
            self.createPaymentLink(secretKey: secretKey, priceId: priceId, paymentSuccessUrl: paymentSuccessUrl, paymentCancelUrl: paymentCancelUrl, callbackId: callbackId)
        }
    }
    
    private func createPaymentLink(secretKey: String, priceId: String, paymentSuccessUrl: String, paymentCancelUrl: String?, callbackId: String) {
        var requestBody = "line_items[0][price]=\(priceId)&line_items[0][quantity]=1&mode=payment&success_url=\(paymentSuccessUrl)"
        if let paymentCancelUrl = paymentCancelUrl {
            requestBody += "&cancel_url=\(paymentCancelUrl)"
        }
        apiCall(secretKey: secretKey, path: "/checkout/sessions", method: "POST", body: requestBody) { response, error in
            guard let response = response, let paymentLink = response["url"] as? String else {
                self.sendErrorResult(error ?? "Failed to create payment link", callbackId: callbackId)
                return
            }
            self.sendSuccessResult(paymentLink, callbackId: callbackId)
        }
    }
    
    private func createSubscriptionLink(secretKey: String, priceId: String?, customerID: String?, itemQuantity: Int, paymentSuccessUrl: String, paymentCancelUrl: String?, callbackId: String) {
        var requestBody = "line_items[0][price]=\(priceId ?? "")&line_items[0][quantity]=\(itemQuantity)&mode=subscription&success_url=\(paymentSuccessUrl)&customer=\(customerID ?? "")"
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
