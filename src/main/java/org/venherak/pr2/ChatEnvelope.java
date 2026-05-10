package org.venherak.pr2;

record ChatEnvelope(String from, String destination, String kind, String payload) {
    String asServerEvent() {
        return "MESSAGE\t" + from + "\t" + destination + "\t" + kind + "\t" + payload;
    }
}
