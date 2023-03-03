package com.example.flightbot;

import com.example.flightbot.enums.Season;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import opennlp.tools.doccat.*;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.*;
import opennlp.tools.util.model.ModelUtil;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Random;
import java.util.ResourceBundle;


public class Controller implements Initializable {

    @FXML
    public Button submitButton;
    @FXML
    private TextField userInput;
    @FXML
    private ListView<HBox> chatListView;

    private int messagecounter = 0;

    private InMemoryDatabase inMemoryDatabase;

    private static String[] detectSentences(String sentence) {
        InputStream sentenceModelInput = null;
        SentenceModel sentenceModel = null;
        SentenceDetectorME sentenceDetector;
        //Load sentence detector model
        try {
            sentenceModelInput = new FileInputStream("opennlp/opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        //Instantiate SentenceModel class
        try {
            sentenceModel = new SentenceModel(sentenceModelInput);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Instantiate SentenceDetectorMe class (splits text into sentences
        sentenceDetector = new SentenceDetectorME(sentenceModel);
        //Detect sentences from text using sentDetect() method
        String sentences[] = sentenceDetector.sentDetect(sentence);
        return sentences;

    }

    private static String[] tokenize(String text) {
        InputStream tokenModelIn = null;
        TokenizerModel tokenizerModel = null;
        TokenizerME tokenizer = null;
        try {
            tokenModelIn = new FileInputStream("opennlp/opennlp-en-ud-ewt-tokens-1.0-1.9.3.bin");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            tokenizerModel = new TokenizerModel(tokenModelIn);
        } catch (IOException e) {
            e.printStackTrace();
        }
        tokenizer = new TokenizerME(tokenizerModel);

        String[] tokens = tokenizer.tokenize(text);

        return tokens;

    }

    private static String[] posTag(String[] tokens) throws Exception {
        /**
         * NN - noun, NNP - proper noun
         * DT - determinder
         * VB, VBD, VBZ - verb (base, past tense, singular present, respectively)
         * IN - preposition/conjunction
         * TO - to
         * JJ - adjective
         */
        //Load the model
        InputStream inputStream = new FileInputStream("opennlp/opennlp-en-ud-ewt-pos-1.0-1.9.3.bin");
        // Instantiate the model
        POSModel posModel = new POSModel(inputStream);
        // Instantiate the posTaggerME class, which will tag each token
        POSTaggerME posTaggerME = new POSTaggerME(posModel);
        //Generate pos tags using the tag() method
        String[] tags = posTaggerME.tag(tokens);

        return tags;
    }

    private static String[] lemmatize(String[] tokens, String[] posTags) throws Exception {
        InputStream inputStream = new FileInputStream("opennlp/en-lemmatizer.bin");
        LemmatizerModel lemmatizerModel = new LemmatizerModel(inputStream);
        LemmatizerME lemmatizerME = new LemmatizerME(lemmatizerModel);
        String[] lemmaTokens = lemmatizerME.lemmatize(tokens, posTags);
        return lemmaTokens;
    }

    private static DoccatModel trainCategorizerModel() throws IOException {
        InputStreamFactory inputStreamFactory = new MarkableFileInputStreamFactory(new File("training_models/categories.txt"));
        ObjectStream<String> lineStream = new PlainTextByLineStream(inputStreamFactory, StandardCharsets.UTF_8);
        ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream);

        DoccatFactory doccatFactory = new DoccatFactory(new FeatureGenerator[]{new BagOfWordsFeatureGenerator()});

        TrainingParameters parameters = ModelUtil.createDefaultTrainingParameters();
        parameters.put(TrainingParameters.CUTOFF_PARAM, 0);

        DoccatModel doccatModel = DocumentCategorizerME.train("en", sampleStream, parameters, doccatFactory);
        return doccatModel;
    }

    private static String categorize(DoccatModel doccatModel, String[] tokens)   {
        DocumentCategorizerME documentCategorizer = new DocumentCategorizerME(doccatModel);

        double[] probabilitiesOfOutcomes = documentCategorizer.categorize(tokens);
        String category = documentCategorizer.getBestCategory(probabilitiesOfOutcomes);
        System.out.println("Category: " + category);
        return category;
    }

    private static String findCategory(String text) throws Exception {
        //Detect sentences
        String[] sentences = detectSentences(text);
        String[] tokens = tokenize(text);
        String[] posTags = posTag(tokens);
        String[] lemmas = lemmatize(tokens, posTags);
        DoccatModel model = trainCategorizerModel();
        String category = categorize(model, lemmas);
        return category;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        inMemoryDatabase = new InMemoryDatabase();
        inMemoryDatabase.setUpDB();

        HBox chatItem = null;
        try {
            chatItem = FXMLLoader.load(getClass().getResource("chat_item_template.fxml"));
        } catch (IOException e) {
            e.printStackTrace();
        }


        Label chatLabel = (Label) chatItem.lookup("#chatLabel");
        chatLabel.setText("Flightbot: Hi my name is SkyMax & I am here to facilitate your flight experience. How can I help you? (If you want to get to know my skills, ask for it in the chat. E.g.: 'How can you help me?' or 'What skills do you have?'");
        chatListView.getItems().add(messagecounter, chatItem);

        userInput.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.ENTER) {
                try {
                    handleInput();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        submitButton.setOnAction(event -> {
            try {
                handleInput();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }

    public void handleInput() throws Exception {
        String input = userInput.getText();

        String category = findCategory(input);
        messagecounter++;
        try {
            HBox chatItem = FXMLLoader.load(getClass().getResource("chat_item_template.fxml"));
            Label chatLabel = (Label) chatItem.lookup("#chatLabel");
            chatLabel.setText("User: " + input);
            chatListView.getItems().add(messagecounter, chatItem);
        } catch (IOException e) {
            e.printStackTrace();
        }


        messagecounter++;
        String response = respond(category, input);
        try {
            HBox chatItem = FXMLLoader.load(getClass().getResource("chat_item_template.fxml"));
            Label chatLabel = (Label) chatItem.lookup("#chatLabel");
            chatLabel.setText("Flightbot: " + response);
            chatListView.getItems().add(messagecounter, chatItem);
        } catch (IOException e) {
            e.printStackTrace();
        }

        userInput.setText("");
    }

    private String respond(String category, String userInput) throws Exception {
        Enum<Season> season = getSeason();
        String respond = "";
        String bookingNumber;
        String destination;
        String date;
        String referenceNumber;
        String[] continueC = {"Is there anything else I can help you with?", "Do you need further assistance?"};
        String[] gateInfo = {"Please provide me your booking number: ", "May I have your booking number please: "};
        String[] end = {"Goodbye!", "Bye", "See you next time", "Goodbye and have a nice day!"};
        String[] travelRec = {"Do you want a recommendation for city trips, beach vacation or based on the current season? ", "Please tell me if you would like a recommendation based on the current season or for city trips or beach trips."};
        String[] beachRec = {"Fly to Venice (Italy) and spend some time in Jesolo which is not far away", "I can recommend Cyprus", "Malta is always worth a visit"};
        String[] cityRec = {"Fly to Zurich (Switzerland) and enjoy the time there.", "Based on the most booked flights, Porto is a good choice for a city trip.", ""};
        String[] springTravelRec = {"I can recommend a trip to New York", "Tokyo in spring is always worth a visit", "Madrid, Lisbon or Marseilles are nice places to visit"};
        String[] summerTravelRec = {"Barcelona, Amsterdam and Los Angeles are the top three visited cities at the moment.", "I heard that Neapel is a beautiful city",};
        String[] autumnTravelRec = {"Transylvania (Romania) is one of the best places in Europe to see stunning fall colours", "In Lapland (Finland) the Northern Lights are at their strongest at this time of the year", "I can recommend a city trip to Munich"};
        String[] winterTravelRec = {"I can recommend Prague (Czech republic)", "Rovaniemi in Finland is a nice place to visit", "If you want to escape the cold, then book a flight to Cancun (Mexico)", "Tenerife in Spain is one of the best winter destinations in Europe"};
        String[] serviceAgent = {"Please call: +4360606060 or write an e-mail to help@service.com.", "Wait a moment, I am redirecting you to a service agent...", "If I cannot help you anymore, please contact a service agent at the service desk or call him on the phone"};
        String[] handleAngryPassenger = {"I understand that this is a frustrating situation, and I want to assure you that we're doing everything we can to help you.", "I apologize for the inconvenience and want to make sure we find a solution that works for you.", "I know that your time is valuable and we appreciate your patience as we work to resolve this issue.", "We understand that this is not the experience you were expecting, and we're committed to making it right.", "We apologize for any inconvenience caused by the cancellation of your flight. Unfortunately, there are times when we have to cancel flights due to unforeseen circumstances or safety concerns. We always do our best to communicate these changes as soon as possible."};
        String[] departureTimes1 = {"03:35 p.m.", "01:45 p.m.", "09:05 p.m.", "07:25 a.m.", "10:00 a.m., 06:00 p.m."};
        String[] departureTimes2 = {"04:35 p.m.", "02:45 p.m.", "10:05 p.m.", "05:25 a.m.", "11:00 a.m., 07:00 p.m."};
        String[] departureTimes3 = {"05:35 p.m.", "03:45 p.m.", "11:05 p.m.", "06:25 a.m.", "12:00 a.m., 08:00 p.m."};
        String[] skills = {"I can recommend destinations based on the current season, or depending on whether you want to spend time at the beach or exploring new cities. Also, you can ask me for the current time in a European city. If a flight is canceled, I'm here to help you get a refund or book another flight. I can also help you find the right gate for your departure and if I can't help you any further, I will refer you to a service representative. I can also take the first steps to booking a flight"};
        String[] baggageInfo = {"A piece of luggage may have a maximum circumference of 158 cm (height + width + length) and weigh a maximum of 23 kg - or 32 kg in Business Class.", "You can take 2 pieces of luggage (one hand luggage and one checked luggage) on your flight for free. Each additional piece of luggage must be paid."};
        switch (category) {
            case "ask-skills":
                respond = skills[(int) (Math.random() * continueC.length)];
                break;
            case "continueC":
                respond = continueC[(int) (Math.random() * continueC.length)];
                break;
            case "ask-gate-info":
                respond = gateInfo[(int) (Math.random() * gateInfo.length)];
                break;
            case "provide-booking-number":
                bookingNumber = getBookingNumber(userInput);
                respond = inMemoryDatabase.getGateInfo(bookingNumber);
                break;
            case "ask-flight-recommendation":
                respond = travelRec[(int) (Math.random() * travelRec.length)];
                break;
            case "seasonal-recommendation":
                if (Season.SPRING == season) {
                    respond = springTravelRec[(int) (Math.random() * springTravelRec.length)];
                } else if (Season.SUMMER == season) {
                    respond = summerTravelRec[(int) (Math.random() * summerTravelRec.length)];
                } else if (Season.AUTUMN == season) {
                    respond = autumnTravelRec[(int) (Math.random() * autumnTravelRec.length)];
                } else if (Season.WINTER == season) {
                    respond = winterTravelRec[(int) (Math.random() * winterTravelRec.length)];
                }
                break;
            case "beach-recommendation":
                respond = beachRec[(int) (Math.random() * beachRec.length)];
                break;
            case "city-recommendation":
                respond = cityRec[(int) (Math.random() * cityRec.length)];
                break;
            case "end":
                respond = end[(int) (Math.random() * end.length)];
                break;
            case "destination-time":
                destination = getLocation(userInput);
                String dateTime = getDateTimeAtLocation(destination);
                respond = "The current date and time in " + destination + ": " + dateTime;
                break;
            case "ask-service-agent":
                respond = serviceAgent[(int) (Math.random() * serviceAgent.length)];
                break;

            case "angry-passenger":
                respond = handleAngryPassenger[(int) (Math.random() * handleAngryPassenger.length)];
                break;
            case "ask-flights":
                destination = getLocation(userInput);
                date = getDate(userInput);
                if (destination.equals("") || date.equals("")) {
                    respond = "Please provide a destination and a date for your booking or try to rephrase your statement";
                } else {
                    respond = "I can offer you a flight to " + destination + "on " + date + " at the following departure times:\n" +
                            "Option 1: " + departureTimes1[(int) (Math.random() * departureTimes1.length)] + "\n" +
                            "Option 2: " + departureTimes2[(int) (Math.random() * departureTimes2.length)] + "\n" +
                            "Option 3: " + departureTimes3[(int) (Math.random() * departureTimes3.length)] + "\n" +
                            "Please tell me the option you prefer: ";
                }
                break;
            case "choose-option":
                referenceNumber = createReferenceNumber();
                if (userInput.contains("1")) {
                    respond = "Thank you for choosing Option 1. I created a case for you and now please contact a service agent to complete your booking. Here is your reference number: " + referenceNumber + ".";
                } else if (userInput.contains("2")) {
                    respond = "Thanks for choosing Option 2. I created a case for you and now please contact a service agent to complete your booking. Here is your reference number: " + referenceNumber + ".";
                } else if (userInput.contains("3")) {
                    respond = "Thank you for choosing Option 3. I created a case for you and now please contact a service agent to complete your booking. Here is your reference number: " + referenceNumber + ".";
                } else {
                    respond = "Sorry, I don't understand. Please try rephrasing your statement.";
                }
                break;
            case "baggage-info":
                respond = baggageInfo[(int) (Math.random() * baggageInfo.length)];
                break;
            default:
                respond = "Sorry, I don't understand. Please try rephrasing your statement.";
                break;
        }
        return respond;

    }

    private Enum<Season> getSeason() {
        LocalDate localDate = java.time.LocalDate.now();
        int month = localDate.getMonthValue();
        if (month >= 3 && month <= 5) {
            return Season.SPRING;
        } else if (month >= 6 && month <= 8) {
            return Season.SUMMER;
        } else if (month >= 9 && month <= 11) {
            return Season.AUTUMN;
        } else {
            return Season.WINTER;
        }
    }

    private String getDate(String userInput) throws IOException {

        InputStream inputStream = new FileInputStream("opennlp/en-ner-date.bin");
        TokenNameFinderModel tokenNameFinderModel = new TokenNameFinderModel(inputStream);
        NameFinderME nameFinderME = new NameFinderME(tokenNameFinderModel);

        String[] tokens = tokenize(userInput);
        Span[] dateSpans = nameFinderME.find(tokens);

        for (Span date : dateSpans) {
            StringBuilder entity = new StringBuilder();
            System.out.println(date);

            for (int i = date.getStart(); i < date.getEnd(); i++) {
                entity.append(tokens[i]).append(" ");
            }

            System.out.println(date.getType() + " : " + entity + "\t [probability=" + date.getProb() + "]");
            return entity.toString();


        }
        return "";


    }

    private String getLocation(String userInput) throws IOException {
        //Function to get a Location from a User Input


        InputStream inputLocation = new FileInputStream("opennlp/en-ner-location.bin");
        TokenNameFinderModel locationFinderModel = new TokenNameFinderModel(inputLocation);
        NameFinderME nameFinderLocation = new NameFinderME(locationFinderModel);

        String[] tokens = tokenize(userInput);

        Span[] locationSpans = nameFinderLocation.find(tokens);


        for (Span city : locationSpans) {
            StringBuilder entity = new StringBuilder();
            System.out.println(city);

            for (int i = city.getStart(); i < city.getEnd(); i++) {
                entity.append(tokens[i]).append(" ");
            }

            System.out.println(city.getType() + " : " + entity + "\t [probability=" + city.getProb() + "]");
            return entity.toString();


        }
        return "";
    }

    private String getDateTimeAtLocation(String city) {
        String formattedCity = city.replace(" ", "");
        String pattern = "MM/dd/yyyy HH:mm:ss";
        DateTimeZone timeZone = DateTimeZone.forID("Europe/" + formattedCity);
        DateTime currentTime = new DateTime(timeZone);
        String patternTime = currentTime.toString(DateTimeFormat.forPattern(pattern));

        return currentTime.dayOfWeek().getAsShortText() + " " + patternTime;
    }

    private String getBookingNumber(String userInput) throws IOException {
        String bookingNumber = "";

        InputStream inputStream = new FileInputStream("opennlp/ner-booking-number-model.bin");
        TokenNameFinderModel tokenNameFinderModel = new TokenNameFinderModel(inputStream);
        NameFinderME nameFinderBookingNumber = new NameFinderME(tokenNameFinderModel);

        String[] tokens = tokenize(userInput);
        Span[] bookingNumberSpans = nameFinderBookingNumber.find(tokens);

        for (Span bookingN : bookingNumberSpans) {
            String entity = "";
            System.out.println(bookingNumber);
            for (int i = bookingN.getStart(); i < bookingN.getEnd(); i++) {
                entity += tokens[i] + " ";
            }
            System.out.println(bookingN.getType() + " : " + entity + "\t [probability=" + bookingN.getProb() + "]");
            return entity;
        }
        return bookingNumber;

    }

    private String createReferenceNumber() {
        Random rdm = new Random(System.currentTimeMillis());
        int rdmNumber = (1 + rdm.nextInt(2)) * 10000 + rdm.nextInt(10000);
        return String.valueOf(rdmNumber);
    }

}