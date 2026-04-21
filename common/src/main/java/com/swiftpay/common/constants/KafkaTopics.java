package com.swiftpay.common.constants;

public final class KafkaTopics {
    public static final String PAYMENT_INITIATED  = "payment.initiated";
    public static final String PAYMENT_COMPLETED  = "payment.completed";
    public static final String PAYMENT_FAILED     = "payment.failed";
    public static final String PAYMENT_STATUS_UPDATE = "payment.status.update";

    private KafkaTopics() {}
}
