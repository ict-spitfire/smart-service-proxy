package eu.spitfire.ssp.backends;


/**
 * Empty interface implemented by all classes whose instances are allowed as results on modification attempts on a
 * {@link eu.spitfire.ssp.backends.generic.DataOrigin}, i.e. update or deletion.
 *
 * @author Oliver Kleine
 */
public interface DataOriginModificationResult extends DataOriginAccessResult{
}
