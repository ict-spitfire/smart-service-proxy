package eu.spitfire_project.smart_service_proxy.backends.wiselibcoap;

import eu.spitfire_project.smart_service_proxy.core.wiselib_interface.WiselibProtocol;

/**
 * Created by IntelliJ IDEA.
 * User: maxpagel
 * Date: 06.07.12
 * Time: 16:46
 * To change this template use File | Settings | File Templates.
 */
public interface CoapListener {
    public void onSemanticDescription(WiselibProtocol.SemanticEntity se);
}
