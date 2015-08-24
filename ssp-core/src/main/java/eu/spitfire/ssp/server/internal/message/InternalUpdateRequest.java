package eu.spitfire.ssp.server.internal.message;

import com.google.common.util.concurrent.SettableFuture;
import org.apache.jena.update.UpdateRequest;

/**
 * Created by olli on 06.07.15.
 */
public class InternalUpdateRequest {

    private UpdateRequest updateRequest;
    private SettableFuture<Void> updateFuture;

    public InternalUpdateRequest(UpdateRequest updateRequest){
        this.updateRequest = updateRequest;
        this.updateFuture = SettableFuture.create();
    }


    public UpdateRequest getUpdateRequest() {
        return updateRequest;
    }

    public SettableFuture<Void> getUpdateFuture() {
        return updateFuture;
    }
}
