// https://github.com/android/connectivity-samples/blob/bbbd6f5db3e0ffdc0b92b172053b021456e4df8c/NearbyConnectionsWalkieTalkie/app/src/main/java/com/google/location/nearby/apps/walkietalkie/ConnectionsActivity.java#L555

package com.example.edgesum.util.nearby;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class Endpoint {
    final String id;
    final String name;
    boolean connected;

    Endpoint(String id, String name, boolean connected) {
        this.id = id;
        this.name = name;
        this.connected = connected;
    }

    Endpoint(String id, String name) {
        this.id = id;
        this.name = name;
        this.connected = false;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Endpoint) {
            Endpoint other = (Endpoint) obj;
            return id.equals(other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("Endpoint{id=%s, name=%s}", id, name);
    }

    static int getIndexById(List<Endpoint> endpoints, String endpointId) {
        for (int index = 0; index < endpoints.size(); index++) {
            if (endpoints.get(index).id.equals(endpointId)) {
                return index;
            }
        }

        return -1;
    }

    static ArrayList<String> getConnectedEndpointIds(List<Endpoint> endpoints) {
        ArrayList<String> endpointIds = new ArrayList<>();

        for (Endpoint endpoint : endpoints) {
            if (endpoint.connected) {
                endpointIds.add(endpoint.id);
            }
        }

        return endpointIds;
    }
}
