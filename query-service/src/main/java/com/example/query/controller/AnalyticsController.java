package com.example.query.controller;

import com.example.query.streams.TopologyProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final TopologyProvider topologyProvider;

    public AnalyticsController(TopologyProvider topologyProvider) {
        this.topologyProvider = topologyProvider;
    }

    @GetMapping("/topology")
    public String topology() {
        return topologyProvider.describe();
    }
}
