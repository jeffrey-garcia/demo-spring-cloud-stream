package com.example.demo;

import com.example.demo.service.ChannelInterceptorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@EnableDiscoveryClient
@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Autowired
	@Qualifier("channelInterceptorService")
	ChannelInterceptorService interceptorService;

	@Bean
	public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		return (args) -> {
			interceptorService.configureCommand("input", (message, messageChannel) -> {
				// Demonstrate how to override the intercept command with custom handling
				return message;
			});
		};
	}

}
