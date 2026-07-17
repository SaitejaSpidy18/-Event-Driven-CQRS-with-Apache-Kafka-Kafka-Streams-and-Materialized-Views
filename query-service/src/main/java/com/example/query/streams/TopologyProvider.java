package com.example.query.streams;

import org.apache.kafka.streams.Topology;
import org.springframework.stereotype.Component;

@Component
public class TopologyProvider {

    private Topology topology;

    public void setTopology(Topology topology) {
        this.topology = topology;
    }

    public String describe() {
        return topology == null ? "Topology not initialized" : topology.describe().toString();
    }
}
