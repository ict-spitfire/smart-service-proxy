//package eu.spitfire.ssp.backends;
//
//
//import eu.spitfire.ssp.server.internal.messages.responses.AccessResult;
//
///**
// * Empty interface implemented by all classes whose instances are allowed as results on modification attempts on a
// * {@link eu.spitfire.ssp.backends.generic.DataOrigin}, i.e. update or deletion.
// *
// * @author Oliver Kleine
// */
//public abstract class DataOriginModificationResult extends AccessResult implements DataOriginInquiryResult{
//
//    /**
//     * Creates a new instance of {@link eu.spitfire.ssp.server.internal.messages.responses.AccessResult}.
//     *
//     * @param resultCode the {@link eu.spitfire.ssp.server.internal.messages.responses.AccessResult.Code} indicating whether
//     *                   the access was successful or not.
//     */
//    public DataOriginModificationResult(Code resultCode, String message) {
//        super(resultCode);
//    }
//}
