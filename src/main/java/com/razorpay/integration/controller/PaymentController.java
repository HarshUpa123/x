package com.razorpay.integration.controller;

import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.catalina.valves.JsonAccessLogValve;
import org.apache.commons.codec.binary.Hex;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import com.razorpay.RazorpayException;
import com.razorpay.integration.entities.Order;
import com.razorpay.integration.entities.Product;
import com.razorpay.integration.service.RazorpayService;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class PaymentController {

	private final RazorpayService razorpayService;

	@Value("${razorpay.secret.key}")
	private String razorpaySecretKey;

	public PaymentController() throws RazorpayException {
		this.razorpayService = new RazorpayService();
	}

	@PostMapping("/create-order")
	public ModelAndView createOrder(@RequestParam String productId, @RequestParam int quantity) {
		try {
			Long productLongId = Long.parseLong(productId);
			Product product = new Product(productLongId, "Demo Product", 50000);

			Order order = new Order(1L, product, quantity);
			com.razorpay.Order orderJson = razorpayService.createOrder(order);
			String orderId = orderJson.get("id").toString();
			System.err.println(orderId);

			String paymentLink = razorpayService.getPaymentLink(orderId);

			ModelAndView modelAndView = new ModelAndView("payment");
			modelAndView.addObject("paymentLink", paymentLink);
			modelAndView.addObject("orderJson", orderJson);
			System.err.println(orderJson);

			return modelAndView;

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@PostMapping("/model")
	public ResponseEntity<String> handleCallback(HttpServletRequest request, @RequestBody String payload) {

		System.out.println(" body in payload " + payload);

		String paymentId = request.getParameter("razorpay_payment_id");
		String orderId = request.getParameter("razorpay_order_id");

		if (verifySignature(paymentId, orderId) != null) {
			System.out.println("Payment Successful!");
			return ResponseEntity.ok("Payment Successful");
		} else {
			return ResponseEntity.status(400).body("Invalid Signature");
		}
	}

	private String verifySignature(String paymentId, String orderId) {
		try {

			System.err.println("verify" + paymentId);

			String payload = orderId + "|" + paymentId;
			String computedSignature = computeHMACSHA256(payload, razorpaySecretKey);
			System.err.println("{}{}{" + computedSignature);
			return computedSignature;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private String computeHMACSHA256(String payload, String secret) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
		mac.init(secretKeySpec);
		byte[] hash = mac.doFinal(payload.getBytes());
		return Hex.encodeHexString(hash);
	}

	@PostMapping("/capture-payment")
	public ResponseEntity<?> capturePayment(@RequestParam String paymentId, @RequestParam int amount) {
		try {
			String response = razorpayService.capturePayment(paymentId, amount);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.status(500).body("Error capturing payment: " + e.getMessage());
		}
	}

}
