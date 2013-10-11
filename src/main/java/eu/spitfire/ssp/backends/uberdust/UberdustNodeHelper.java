package eu.spitfire.ssp.backends.uberdust;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import eu.spitfire.ssp.utils.Language;
import eu.uberdust.communication.UberdustClient;
import eu.wisebed.wisedb.model.Testbed;
import org.apache.log4j.Logger;
import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Conversion Class for Uberdust Node Readings to their semantic descriptions.
 *
 * @author Dimitrios Amaxilatis
 */
public class UberdustNodeHelper {
    /**
     * Logger.
     */
    private static Logger log = Logger.getLogger(UberdustNodeHelper.class.getName());
    /**
     * Pattern used to detect a lightZone actuator.
     */
    private static Pattern lightZone = Pattern.compile(":lz[1-9][0-9]*", Pattern.CASE_INSENSITIVE);
    /**
     * Pattern used to detect a HVAC actuator.
     */
    private static Pattern fan = Pattern.compile(":ac[1-9][0-9]*", Pattern.CASE_INSENSITIVE);
    /**
     * Pattern used to detect a PowerPlug/Relay actuator.
     */
    private static Pattern relay = Pattern.compile(":[1-9][0-9]*r", Pattern.CASE_INSENSITIVE);

    private static Map<String, Set<String>> attachedSystems = new HashMap<>();
    private static Map<String, String> tinyURIS = new HashMap<String, String>();
    private static SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yy-MM-dd'T'HH:mm:ss'Z'");


    /**
     * Constructor for the node reading.
     *
     * @param name       the name of the device to annotate.
     * @param testbed    the testbed of the device.
     * @param prefix     the prefix of the testbed.
     * @param capability the capability annotated.
     * @param value      the value of the measurement.
     * @param time       the timestamp associated by the timestamp.
     * @throws org.json.JSONException
     */
    public static Model generateDescription(String name, String testbed, String prefix, String capability, Double value, Date time) {
        List<String> rooms, workstation;
        String locationName, capabilityResource, y, x;

        try {
            x = UberdustClient.getInstance().getNodeX(testbed, name);
        } catch (IOException e) {
            x = "0";
        }

        try {
            y = UberdustClient.getInstance().getNodeY(testbed, name);
        } catch (IOException e) {
            y = "0";
        }

        try {
            rooms = UberdustClient.getInstance().getNodeRooms(Integer.parseInt(testbed), name);
        } catch (Exception e) {
            rooms = null;
        }

        try {
            workstation = UberdustClient.getInstance().getNodeWorkstations(Integer.parseInt(testbed), name);
        } catch (Exception e) {
            workstation = null;
        }

        try {
            capabilityResource = getSameAs(getCapabilityResourceURI(testbed, capability));
        } catch (URISyntaxException e) {
            capabilityResource = "null";
        }

        try {
            locationName = ((Testbed) UberdustClient.getInstance().getTestbedById(Integer.parseInt(testbed))).getName();
        } catch (IOException e) {
            locationName = prefix;
        } catch (JSONException e) {
            locationName = prefix;
        }
        if (locationName.equals("Gen6")) {
            try {
                locationName = (UberdustClient.getInstance().getLastNodeReading(Integer.parseInt(testbed), name, "name").getJSONArray("readings").getJSONObject(0)).getString("stringReading");
            } catch (Exception e) {
                log.error(e);
            }
        }

        return getModel(testbed, prefix, capabilityResource, x, y, time, capability, locationName, String.valueOf(value), rooms, workstation, name);
    }

    public static Statement createUpdateValueStatement(URI subject, Double value) throws Exception {
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        StringBuilder description = new StringBuilder();
        description.append("<" + subject + "> ").append("<http://spitfire-project.eu/ontology/ns/value> ").append("\"" + value + "\"^^<http://www.w3.org/2001/XMLSchema#float>.\n");
        Model model = ModelFactory.createDefaultModel();
        ByteArrayInputStream bin = new ByteArrayInputStream(description.toString().getBytes(Charset.forName("UTF-8")));
        model.read(bin, null, Language.RDF_N3.lang);

        return model.listStatements().nextStatement();
    }

    public static Statement createUpdateTimestampStatement(URI subject, Date timestamp) throws Exception {
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        StringBuilder description = new StringBuilder();
        description.append("<" + subject + "> ").append("<http://purl.org/dc/terms/#date> ").append("\"" + dateFormatGmt.format(timestamp) + "\";\n");

        Model model = ModelFactory.createDefaultModel();
        ByteArrayInputStream bin = new ByteArrayInputStream(description.toString().getBytes(Charset.forName("UTF-8")));
        model.read(bin, null, Language.RDF_N3.lang);

        return model.listStatements().nextStatement();
    }


    /**
     * N3 RDF description of the node.
     *
     * @return a string containing the N3 rdf description.
     * @throws java.net.URISyntaxException should not happen.
     */
    public static String toNEWRDF(
            final String testbed,
            final String prefix,
            final String capabilityResource,
            final String x,
            final String y,
            final Date time,
            final String capability,
            final String locationName,
            final String value,
            final List<String> rooms,
            final List<String> workstations,
            final String node

    ) throws URISyntaxException {
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        final URI resourceURI = new URI(getResourceURI(testbed, node, capability));

        StringBuilder description = new StringBuilder();
        updateAttachedSystems(node, capability);

        Iterator<String> asi = attachedSystems.get(node).iterator();
        StringBuilder asObject = new StringBuilder();
        while (asi.hasNext()) {
            asObject.append(",").append("<" + getResourceURI(testbed, node, asi.next()) + ">");
        }
        description.append("<" + getDeviceURI(testbed, node) + "> ").append("<http://purl.oclc.org/NET/ssnx/ssn#attachedSystem> ").append(asObject.toString().substring(1) + ";\n");

        description.append("<http://www.w3.org/2003/01/geo/wgs84_pos#long> ").append("\"" + y + "\"^^<http://www.w3.org/2001/XMLSchema#float>;\n");
        description.append("<http://www.w3.org/2003/01/geo/wgs84_pos#lat> ").append("\"" + x + "\"^^<http://www.w3.org/2001/XMLSchema#float>;\n");
        description.append("<http://www.ontologydesignpatterns.org/ont/dul/DUL.owl/hasLocation> ").append("\"" + locationName + "\".\n");

        if (rooms != null) {
            final StringBuilder object = new StringBuilder();
            for (String room : rooms) {
                object.append(",<" + "http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:room:" + room + "/>");
            }
            description.append("<" + getDeviceURI(testbed, node) + "> ").append("<http://purl.oclc.org/NET/ssnx/ssn#featureOfInterest> ").append(object.toString().substring(1) + ". \n");
            for (String room : rooms) {
                description.append("<" + "http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:room:" + room + "/> ").append("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ").append("<http://spitfire-project.eu/foi/Room>. \n");
            }
        }

        if (workstations != null) {
            final StringBuilder object = new StringBuilder();
            for (String workstation : workstations) {
                object.append(",<http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:workstation:" + workstation + "/>");
            }
            description.append("<" + getDeviceURI(testbed, node) + "> ").append("<http://purl.oclc.org/NET/ssnx/ssn#featureOfInterest> ").append(object.toString().substring(1) + ". \n");
            for (String workstation : workstations) {
                description.append("<" + "http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:workstation:" + workstation + "/> ").append("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>").append("<http://www.freebase.com/m/0v3bzfq>. \n");
            }
        }
        if ((lightZone.matcher(capability).find() || relay.matcher(capability).find())) {
            String fullURI = getResourceURI(testbed, node, capability);
            String tinyURI = tiny(getResourceURI(testbed, node, capability));
            tinyURIS.put(tinyURI, fullURI);
            description.append("<" + fullURI + "> ").append("<http://www.w3.org/2002/07/owl#sameAs> ").append("<" + tinyURI + ">.\n");
            description.append("<" + tinyURI + ">").append("<http://www.w3.org/2002/07/owl#sameAs> ").append("<" + fullURI + ">.\n");

            description.append("<" + getResourceURI(testbed, node, capability) + "> ").append("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ").append("<http://spitfire-project.eu/ontology/ns/sn/Switch>;\n");
        } else if (fan.matcher(capability).find()) {
            String fullURI = getResourceURI(testbed, node, capability);
            String tinyURI = tiny(getResourceURI(testbed, node, capability));
            tinyURIS.put(tinyURI, fullURI);
            description.append("<" + fullURI + "> ").append("<http://www.w3.org/2002/07/owl#sameAs> ").append("<" + tinyURI + ">.\n");
            description.append("<" + tinyURI + ">").append("<http://www.w3.org/2002/07/owl#sameAs> ").append("<" + fullURI + ">.\n");

            description.append("<" + getResourceURI(testbed, node, capability) + "> ").append("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ").append("<http://spitfire-project.eu/ontology/ns/sn/Fan>;");
        } else {
            description.append("<" + getResourceURI(testbed, node, capability) + "> ").append("<http://purl.org/dc/terms/#date> ").append("\"" + dateFormatGmt.format(time) + "\";\n");
            description.append("<http://spitfire-project.eu/ontology/ns/obs> ").append("<" + capabilityResource + ">;\n");
        }
        description.append("<http://spitfire-project.eu/ontology/ns/value> ").append("\"" + value + "\"^^<http://www.w3.org/2001/XMLSchema#float>.\n");

        return description.toString();
    }

    private static void updateAttachedSystems(String node, String capability) {
        if (!attachedSystems.containsKey(node)) {
            attachedSystems.put(node, new HashSet<String>());
        }
        attachedSystems.get(node).add(capability);
    }

    /**
     * N3 RDF description of the node.
     *
     * @return a string containing the N3 rdf description.
     * @throws java.net.URISyntaxException should not happen.
     */
    @Deprecated
    public static String toRDF(
            final String testbed,
            final String prefix,
            final String capabilityResource,
            final String x,
            final String y,
            final Date time,
            final String capability,
            final String locationName,
            final String value,
            final List<String> rooms,
            final List<String> workstations,
            final String node

    ) throws URISyntaxException {
        SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yy-MM-dd'T'HH:mm:ss'Z'");
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        final URI resourceURI = new URI(getResourceURI(testbed, node, capability));
        String description = "" +
                "<" + (resourceURI).toString() + ">\n" +
                "<http://www.w3.org/2003/01/geo/wgs84_pos#long>\n" +
                "\"" + y + "\"^^<http://www.w3.org/2001/XMLSchema#float>;\n" +
                "<http://www.w3.org/2003/01/geo/wgs84_pos#lat>\n" +
                "\"" + x + "\"^^<http://www.w3.org/2001/XMLSchema#float>;\n" +
                "<http://purl.org/dc/terms/#date>\n" +
                "\"" + dateFormatGmt.format(time) + "\";\n" +
                "<http://www.ontologydesignpatterns.org/ont/dul/DUL.owl/hasLocation>\n" +
                "\"" + locationName + "\"";
        if ((lightZone.matcher(capability).find() || relay.matcher(capability).find())) {

            description += ";\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#attachedSystem>\n" +
                    "<" + (resourceURI).toString() + "attachedSystem>.\n" +
                    "<" + (resourceURI).toString() + "attachedSystem>\n" +
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#switch>;\n" +
                    "<http://spitfire-project.eu/ontology/ns/value>\n" +
                    "\"" + value + "\"^^<http://www.w3.org/2001/XMLSchema#float>;\n" +
                    "<http://www.w3.org/2002/07/owl#sameAs> " +
                    "<http://uberdust.cti.gr/" + tiny(resourceURI.toString()) + ">.\n" +
                    "<http://uberdust.cti.gr/" + tiny(resourceURI.toString()) + "> " +
                    "<http://www.w3.org/2002/07/owl#sameAs> " +
                    "<" + (resourceURI).toString() + "attachedSystem>.";

        } else if (fan.matcher(capability).find()) {
            description += ";\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#attachedSystem>\n" +
                    "<" + (resourceURI).toString() + "attachedSystem>.\n" +
                    "<" + (resourceURI).toString() + "attachedSystem>\n" +
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\n" +
                    "<http://purl.oclc.org/NET/ssnx/ssn#fan>;\n" +
                    "<http://spitfire-project.eu/ontology/ns/value>\n" +
                    "\"" + value + "\"^^<http://www.w3.org/2001/XMLSchema#float>;\n" +
                    "<http://www.w3.org/2002/07/owl#sameAs> " +
                    "<http://uberdust.cti.gr/" + tiny(resourceURI.toString()) + ">.\n" +
                    "<http://uberdust.cti.gr/" + tiny(resourceURI.toString()) + "> " +
                    "<http://www.w3.org/2002/07/owl#sameAs> " +
                    "<" + (resourceURI).toString() + "attachedSystem>.\n";
        } else {
            description += ";\n" +
                    "<http://spitfire-project.eu/ontology/ns/obs>\n" +
                    "<" + capabilityResource + ">;\n" +
                    "<http://spitfire-project.eu/ontology/ns/value>\n" +
                    "\"" + value + "\"^^<http://www.w3.org/2001/XMLSchema#float>.\n";
        }
        if (rooms != null) {
            for (String room : rooms) {
                description += "<" + (resourceURI).toString() + ">\n" +
                        "<http://purl.oclc.org/NET/ssnx/ssn#featureOfInterest>\n" +
                        "<" + "http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:room:" + room + "/>.\n" +
                        "<" + "http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:room:" + room + "/> \n" +
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\n" +
                        "<http://spitfire-project.eu/foi/Room>.";
            }
        }

        if (workstations != null) {
            for (String workstation : workstations) {
                description += "<" + (resourceURI).toString() + ">\n" +
                        "<http://purl.oclc.org/NET/ssnx/ssn#featureOfInterest> <http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:workstation:" + workstation + "/>.\n" +
                        "<" + "http://uberdust.cti.gr/rest/testbed/" + testbed + "/node/" + prefix + "virtual:workstation:" + workstation + "/>\n" +
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\n" +
                        "<http://www.freebase.com/m/0v3bzfq>.";
            }
        }

        description += "\n" +
                "\n" +
                "<" + capabilityResource + ">\n" +
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\n" +
                "<http://purl.oclc.org/NET/ssnx/ssn#Property>.";
        return description;
    }

    /**
     * Get the URI of the resource associated to the Uberdust Capability.
     *
     * @param uberdustURL the Uberdust Capability URI.
     * @return the URI of the Capability using the Spitfire ontologies.
     */
    private static String getSameAs(String uberdustURL) {
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

    public static String tiny(String resourceURI) {
        return "http://uberdust.cti.gr/" + String.valueOf(resourceURI.hashCode()).replaceAll("-", "");
    }

    /**
     * Returns a Jena Model of the Node.
     *
     * @return the jena model describing the node.
     */
    public static Model getModel(final String testbed,
                                 final String prefix,
                                 final String capabilityResource,
                                 final String x,
                                 final String y,
                                 final Date time,
                                 final String capability,
                                 final String locationName,
                                 final String value,
                                 final List<String> rooms,
                                 final List<String> workstations,
                                 final String name) {
        Model model = ModelFactory.createDefaultModel();
        try {
            ByteArrayInputStream bin = new ByteArrayInputStream(toNEWRDF(
                    testbed,
                    prefix,
                    capabilityResource,
                    x,
                    y,
                    time,
                    capability,
                    locationName,
                    value,
                    rooms,
                    workstations,
                    name
            ).getBytes(Charset.forName("UTF-8")));
            model.read(bin, null, Language.RDF_N3.lang);
        } catch (URISyntaxException e) {

        }
        return model;
    }

    /**
     * Generate the resource uri for the Uberdust Node Capability.
     *
     * @param testbed
     * @param node
     * @param capability
     * @return
     * @throws URISyntaxException
     */
    public static String getResourceURI(final String testbed, final String node, final String capability) throws URISyntaxException {
        return new URI("http", null, "uberdust.cti.gr", -1, "/rest/testbed/" + testbed + "/node/" + node + "/capability/" + capability + "/", null, null).toString();
    }

    public static String getDeviceURI(final String testbed, final String node) throws URISyntaxException {
        return new URI("http", null, "uberdust.cti.gr", -1, "/rest/testbed/" + testbed + "/node/" + node + "/rdf", null, null).toString();
    }

    /**
     * Generate the resource uri for the Uberdust Capability.
     *
     * @param testbed
     * @param capability
     * @return
     * @throws URISyntaxException
     */
    public static String getCapabilityResourceURI(final String testbed, final String capability) throws URISyntaxException {
        return new URI("http", null, "uberdust.cti.gr", -1, "/rest/testbed/" + testbed + "/capability/" + capability + "/rdf", null, null).toString();
    }

    /**
     * Convert a tiny url to a full URI.
     *
     * @param uri
     * @return
     * @throws URISyntaxException
     */
    public static String unWrap(String uri) throws URISyntaxException {
        for (String uri1 : tinyURIS.keySet()) {
            System.out.println("TINY:" + uri1 + "@" + tinyURIS.get(uri1));
        }
        if (tinyURIS.containsKey(uri)) {
            System.out.println("unwrapping:");
            return tinyURIS.get(uri);
        } else {
            return null;
        }
    }
}