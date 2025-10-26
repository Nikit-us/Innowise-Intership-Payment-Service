package com.innowise.feing;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "random-org", url = "${random.org.url}")
public interface RandomOrgClient {
    @GetMapping
    String getRandomNumber();
}
