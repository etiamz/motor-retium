package com.mycompany.app;

import com.mycompany.app.Motor.Agent;

public final class Port {
    private Port() {
    }

    public static final class Consumer {
        private Producer producer;

        public Consumer(final Producer producer) {
            this.producer = producer;
        }

        // Get the immediate producer this consumer is pointing to.
        public Producer producer() {
            return this.producer;
        }

        // Chase the producer chain until we arrive at a Motor agent, then repoint this consumer to
        // the resolved agent.
        public Agent chase() {
            Producer port = this.producer;
            while (port.meaning instanceof Producer forwarder) {
                port = forwarder;
            }
            this.producer = port;
            return (Agent) port.meaning;
        }

        public void setProducer(final Producer producer) {
            this.producer = producer;
        }
    }

    public static final class Producer {
        private Object meaning;

        public Producer(final Agent owner) {
            // This is a physical port attached to its owner.
            this.meaning = owner;
        }

        public void forward(final Producer other) {
            // This is a virtual port; follow the chain.
            this.meaning = other;
        }
    }
}
