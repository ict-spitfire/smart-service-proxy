package eu.spitfire.ssp.backends.files;

import eu.spitfire.ssp.backends.generic.DataOrigin;
import eu.spitfire.ssp.backends.generic.access.DataOriginAccessor;
import eu.spitfire.ssp.backends.generic.access.HttpSemanticProxyWebservice;

import java.net.URI;
import java.nio.file.Path;

/**
 * Created by olli on 11.04.14.
 */
public class HttpSemanticProxyWebserviceForFiles extends HttpSemanticProxyWebservice<Path> {

    @Override
    public DataOriginAccessor<Path> getDataOriginAccessor(DataOrigin<Path> dataOrigin) {
        return null;
    }

    @Override
    public DataOrigin<Path> getDataOrigin(URI proxyUri) {
        return null;
    }
}
