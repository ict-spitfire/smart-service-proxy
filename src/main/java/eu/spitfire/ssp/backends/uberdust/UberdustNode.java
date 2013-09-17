package eu.spitfire.ssp.backends.uberdust;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import eu.spitfire.ssp.server.payloadserialization.Language;
import eu.uberdust.communication.UberdustClient;
import org.apache.log4j.Logger;
import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

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
    private final Double value;
    /**
     * Timestamp of the latest reading.
     */
    private final Date time;
    /**
     * Longtitude.
     */
    private final String x;
    /**
     * Lattitude.
     */
    private final String y;

    /**
     * Constructor for the node reading.
     *
     * @param name
     * @param testbed
     * @param capability
     * @param value
     * @param time
     * @throws JSONException
     */
    public UberdustNode(String name, String testbed, String capability, Double value, Date time) {
        String y1;
        String x1;

        this.name = name;
        this.capability = capability;
        this.value = value;
        this.time = time;
        this.testbed = testbed;

        try {
            x1 = UberdustClient.getInstance().getNodeX(testbed, name);
        } catch (JSONException e) {
            x1 = "0";
        }
        x = x1;
        try {
            y1 = UberdustClient.getInstance().getNodeY(testbed, name);
        } catch (JSONException e) {
            y1 = "0";
        }

        y = y1;
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

        String description = "" +
                "<" + (new URI(UberdustNode.getResourceURI(this))).toString() + ">\n" +
                "<http://spitfire-project.eu/ontology/ns/obs>\n" +
                "<" + UberdustNode.getCapabilityResourceURI(this) + ">;\n" +
                "<http://spitfire-project.eu/ontology/ns/value>\n" +
                "\"" + value + "\"^^<http://www.w3.org/2001/XMLSchema#float>.\n" +
                "" +
                "\n" +
                "\n" +
                "<" + UberdustNode.getCapabilityResourceURI(this) + ">\n" +
                "<http://www.w3.org/2000/01/rdf-schema#type>\n" +
                "<http://purl.oclc.org/NET/ssnx/ssn#Property>;";
        return description;
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
}
