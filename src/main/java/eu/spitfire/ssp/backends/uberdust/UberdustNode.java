package eu.spitfire.ssp.backends.uberdust;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import eu.spitfire.ssp.server.payloadserialization.Language;
import eu.uberdust.communication.UberdustClient;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * Conversion Class for Uberdust Node Readings to their semantic descriptions.
 *
 * @author Dimitrios Amaxilatis
 */
public class UberdustNode {
    /**
     * Logger.
     */
    private static Logger log = Logger.getLogger(UberdustNode.class.getName());

    private static Pattern lightZone = Pattern.compile(":lz[1-9][0-9]*", Pattern.CASE_INSENSITIVE);
    private static Pattern fan = Pattern.compile(":ac[1-9][0-9]*", Pattern.CASE_INSENSITIVE);
    private static Pattern relay = Pattern.compile(":[1-9][0-9]*r", Pattern.CASE_INSENSITIVE);

    private final String capabilityResource;
    private final String workstation;
    private final String prefix;
    /**
     * Node name.
     */
    String name;
    /**
     * Testbed ID.
     */
    String testbed;
    /**
     * Uberdust Capability Name.
     */
    private final String capability;
    /**
     * Double Reading.
     */
    private Double value;
    /**
     * Timestamp of the latest reading.
     */
    private Date time;
    /**
     * Longtitude.
     */
    private final String x;
    /**
     * Lattitude.
     */
    private final String y;
    private String room;

    /**
     * Constructor for the node reading.
     *
     * @param name
     * @param testbed
     * @param prefix
     * @param capability
     * @param value
     * @param time
     * @throws JSONException
     */
    public UberdustNode(String name, String testbed, String prefix, String capability, Double value, Date time) {
        String workstation1;
        String capabilityResource1;
        String y1;
        String x1;

        this.name = name;
        this.capability = capability;
        this.value = value;
        this.time = time;
        this.testbed = testbed;
        this.prefix = prefix;
        try {
            x1 = UberdustClient.getInstance().getNodeX(testbed, name);
        } catch (IOException e) {
            x1 = "0";
        }
        x = x1;

        try {
            y1 = UberdustClient.getInstance().getNodeY(testbed, name);
        } catch (IOException e) {
            y1 = "0";
        }
        y = y1;

        try {
            room = ((JSONObject) ((JSONArray) UberdustClient.getInstance().getLastNodeReading(Integer.parseInt(testbed), name, "room").get("readings")).get(0)).getString("stringReading");
        } catch (Exception e) {
            room = null;
        }

        try {
            workstation1 = ((JSONObject) ((JSONArray) UberdustClient.getInstance().getLastNodeReading(Integer.parseInt(testbed), name, "workstation").get("readings")).get(0)).getString("stringReading");
        } catch (Exception e) {
            workstation1 = null;
        }
        workstation = workstation1;

        try {
            capabilityResource1 = getSameAs(UberdustNode.getCapabilityResourceURI(this));
        } catch (URISyntaxException e) {
            capabilityResource1 = "null";
        }
        capabilityResource = capabilityResource1;


    }

    @Override
    public String toString() {
        return "UberdustNode{" +
                "name='" + name + '\'' +
                ", capability='" + capability + '\'' +
                '}';
    }

    public String toRdfXML() {
        String response =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n" +
                        "  xmlns:ns0=\"http://www.w3.org/2000/01/rdf-schema#\"\n" +
                        "  xmlns:ns1=\"http://purl.oclc.org/NET/ssnx/ssn#\"\n" +
                        "  xmlns:ns2=\"http://spitfire-project.eu/cc/spitfireCC_n3.owl#\"\n" +
                        "  xmlns:ns3=\"http://www.loa-cnr.it/ontologies/DUL.owl#\"\n" +
                        "  xmlns:ns4=\"http://purl.org/dc/terms/\">\n" +
                        "\n";
        SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yy-MM-dd'T'HH:mm'Z'");
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));

        response += "  <rdf:Description rdf:about=\"http://spitfire-project.eu/sensor/" + name + "\">\n" +
                "    <ns0:type rdf:resource=\"http://purl.oclc.org/NET/ssnx/ssn#Sensor\"/>\n" +
                "    <ns1:observedProperty rdf:resource=\"http://spitfire-project.eu/property/" + capability + "\"/>\n" +
                "    <ns3:hasValue>" + value + "</ns3:hasValue>\n" +
                "    <ns4:date>" + dateFormatGmt.format(time) + "</ns4:date>\n" +
                "  </rdf:Description>\n";

        response += "\n" +
                "</rdf:RDF>";

        return response;
    }

    /**
     * N3 RDF description of the node.
     *
     * @return a string containing the N3 rdf description.
     * @throws URISyntaxException should not happen.
     */
    public String toSSP() throws URISyntaxException {
        SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yy-MM-dd'T'HH:mm:ss'Z'");
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));

        String description = "" +
                "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + ">\n" +
                "<http://spitfire-project.eu/ontology/ns/obs>\n" +
                "<" + capabilityResource + ">;\n" +
                "<http://spitfire-project.eu/ontology/ns/value>\n" +
                "\"" + value + "\"^^<http://www.w3.org/2001/XMLSchema#float>;\n" +
                "<http://www.w3.org/2003/01/geo/wgs84_pos#long>\n" +
                "\"" + y + "\"^^<http://www.w3.org/2001/XMLSchema#float>;\n" +
                "<http://www.w3.org/2003/01/geo/wgs84_pos#lat>\n" +
                "\"" + x + "\"^^<http://www.w3.org/2001/XMLSchema#float>;\n" +
                "<http://purl.org/dc/terms/#date>\n" +
                "\"" + dateFormatGmt.format(time) + "\";\n" +
                "<http://www.w3.org/2005/Incubator/ssn/ssnx/ssn#hasLocation>\n" +
                "\"" + prefix + "\"";
        if (lightZone.matcher(capability).find() || relay.matcher(capability).find()) {

            description += ";\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#attachedSystem>\n" +
                    "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + "attachedSystem>.\n" +
                    "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + "attachedSystem>\n" +
                    "<http://www.w3.org/2000/01/rdf-schema#type>\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#switch>.\n";

        } else if (fan.matcher(capability).find()) {
            description += ";\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#attachedSystem>\n" +
                    "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + "attachedSystem>.\n" +
                    "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + "attachedSystem>\n" +
                    "<http://www.w3.org/2000/01/rdf-schema#type>\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#fan>.\n";
        } else {
            description += ".";
        }
        if (room != null) {
            description += "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + ">\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#featureOfInterest>\n" +
                    "<" + "http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:room:" + room + "/>.\n" +
                    "<" + "http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:room:" + room + "/> \n" +
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\n" +
                    "<http://spitfire-project.eu/foi/Room>.";
        }

        if (workstation != null) {
            String[] workstations = workstation.split(",");
            for (String aworkstation : workstations) {
                description += "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + ">\n" +
                        "<http://purl.oclc.org/NET/ssnx/ssn#featureOfInterest> <http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:workstation:" + aworkstation + "/>.\n" +
                        "<" + "http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:workstation:" + aworkstation + "/>\n" +
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\n" +
                        "<http://www.freebase.com/m/0v3bzfq>.";
            }
        }

        description += "\n" +
                "\n" +
                "<" + capabilityResource + ">\n" +
                "<http://www.w3.org/2000/01/rdf-schema#type>\n" +
                "<http://purl.oclc.org/NET/ssnx/ssn#Property>.";
        return description;
    }

    private static String getSameAs(String uberdustURL) {
//        final String uberdustURL = "http://uberdust.cti.gr/rest/testbed/1/capability/urn:wisebed:node:capability:light/rdf";
        String sameAs = uberdustURL;
        try {
            URL url = new URL(uberdustURL);
            Model m = ModelFactory.createDefaultModel();
            m.read(uberdustURL);
            StmtIterator props = m.getResource(uberdustURL).listProperties();
            while (props.hasNext()) {
                Statement something = props.next();
                if ("http://www.w3.org/2002/07/owl#sameAs".equals(something.getPredicate().toString())) {
                    sameAs = something.getObject().toString();
                    if (sameAs.contains("null")) {
                        sameAs = uberdustURL;
                    }
                }
            }
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }
        return sameAs;
    }

    /**
     * Returns a Jena Model of the Node.
     *
     * @return the jena model describing the node.
     */
    public Model getModel() {
        Model model = ModelFactory.createDefaultModel();
        try {
            ByteArrayInputStream bin = new ByteArrayInputStream(toSSP().getBytes(Charset.forName("UTF-8")));
            model.read(bin, null, Language.RDF_N3.lang);
        } catch (URISyntaxException e) {

        }
        return model;
    }

    public static String getResourceURI(final UberdustNode node) throws URISyntaxException {
        return getResourceURI(node.getTestbed(), node.getName(), node.getCapability());
    }

    public static String getResourceURI(final String testbed, final String node, final String capability) throws URISyntaxException {
//        return "http://uberdust.cti.gr/rest/testbed/" + node.getTestbed() + "/node/" + node.getName() + "/capability/" + node.getCapability() + "/";
        return new URI("http", null, "uberdust.cti.gr", -1, "/rest/testbed/" + testbed + "/node/" + node + "/capability/" + capability + "/", null, null).toString();
    }

    public static String getCapabilityResourceURI(final UberdustNode node) throws URISyntaxException {
//        return "http://uberdust.cti.gr/rest/testbed/" + node.getTestbed() + "/capability/" + node.getCapability() + "/rdf";
        return new URI("http", null, "uberdust.cti.gr", -1, "/rest/testbed/" + node.getTestbed() + "/capability/" + node.getCapability() + "/rdf", null, null).toString();
    }

    public String getName() {
        return name;
    }

    public String getCapability() {
        return capability;
    }

    public String getTestbed() {
        return testbed;
    }

    public void update(Double doubleReading, Date date) {
        this.value = doubleReading;
        this.time = date;
    }
}
