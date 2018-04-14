package hashtags.autohash.pojo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContentResponse {

    private String status;
    private List<Uploaded> uploaded = new ArrayList<Uploaded>();
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Uploaded> getUploaded() {
        return uploaded;
    }

    public void setUploaded(List<Uploaded> uploaded) {
        this.uploaded = uploaded;
    }

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }
}
