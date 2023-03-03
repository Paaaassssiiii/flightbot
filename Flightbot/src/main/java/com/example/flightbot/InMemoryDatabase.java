package com.example.flightbot;

import java.util.HashMap;
import java.util.Map;


//InMemoryDB which is used to store booking numbers and get the gate info to each booking.
public class InMemoryDatabase {

    public HashMap<String, String> bookings = new HashMap<String, String>();

    public void setUpDB() {
        insert("7TJ9K", "F01");
        System.out.println(bookings.size());
        insert("8LJ5N", "G03");
        System.out.println(bookings.size());
        insert("1FH4R", "C22");
        System.out.println(bookings.size());
        insert("5KP3M", "B02");
        System.out.println(bookings.size());
        insert("3EZ7G", "A07");
        System.out.println(bookings.size());
        System.out.println(bookings.get("3EZ7G"));

        for (Map.Entry<String, String> entry : bookings.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }

    }

    public void insert(String key, String value) {
        bookings.put(key, value);
    }

    public String getGateInfo(String bookingNumber) {
        String formatteBookingNumber = bookingNumber.replace(" ", "");
        String gate = bookings.get(formatteBookingNumber);
        if (gate == null) {
            System.out.println("No gate found for booking number " + formatteBookingNumber);
            return "No flight found for your booking. Please check if you wrote the booking number correctly";
        }
        return "Your flight takes off from gate: " + gate;
    }
}



