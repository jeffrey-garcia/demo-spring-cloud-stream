package com.example.demo.channel;

import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;

public interface ErrorSink {
    String ERROR = "error";

    @Output(ErrorSink.ERROR)
    MessageChannel errorOne();
}
