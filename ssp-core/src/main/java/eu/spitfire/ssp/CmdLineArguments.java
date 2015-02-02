/**
 * Copyright (c) 2012, Oliver Kleine, Institute of Telematics, University of Luebeck
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *  - Redistributions of source messageCode must retain the above copyright notice, this list of conditions and the following
 *    disclaimer.
 *
 *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
 *    products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package eu.spitfire.ssp;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * Wrapper class for the possible command line options for the Smart Service Proxy
 *
 * @author Oliver Kleine
 */
public class CmdLineArguments {

    @Option(name = "--help",
            usage = "Prints this help")
    private boolean help = false;

    @Option(name = "--log4jConfig",
            usage = "Sets the path to the log4j (XML) configuration file")
    private String log4jConfigPath = null;


    @Option(name = "--sspConfig",
            usage = "Sets the path to the ssp.properties file")
    private String sspPropertiesPath = "./ssp.properties";


    /**
     * Creates a new instance of {@link CmdLineArguments}.
     *
     * @param args the array of command line parameters (forwarded arguments from
     *             <code>public static void main(String[] args)</code>
     *
     * @throws CmdLineException if some error occurred while reading the given command line arguments
     */
    public CmdLineArguments(String[] args) throws CmdLineException {
        CmdLineParser parser = new CmdLineParser(this);

        try{
            parser.parseArgument(args);
        }
        catch(CmdLineException ex){
            System.err.println(ex.getMessage());
            parser.printUsage(System.err);
            throw ex;
        }

        if(this.isHelp()){
            parser.printUsage(System.out);
        }
    }

    /**
     * Returns <code>true</code> if --help was given as console parameter or <code>false</code> otherwise
     * @return <code>true</code> if --help was given as console parameter or <code>false</code> otherwise
     */
    public boolean isHelp() {
        return help;
    }

    /**
     * Returns the path to the log4j (XML-)configuration file or <code>null</code> if not set
     * @return the path to the log4j (XML-)configuration file or <code>null</code> if not set
     */
    public String getLog4jConfigPath() {
        return log4jConfigPath;
    }

    /**
     * Returns the path to the "ssp.properties" configuration file or <code>null</code> if not set
     * @return the path to the "ssp.properties" configuration file or <code>null</code> if not set
     */
    public String getSspPropertiesPath(){
        return this.sspPropertiesPath;
    }
}
