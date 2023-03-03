module com.example.demo {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.opennlp.tools;
    requires org.joda.time;
    requires java.sql;


    opens com.example.flightbot to javafx.fxml;
    exports com.example.flightbot;
}