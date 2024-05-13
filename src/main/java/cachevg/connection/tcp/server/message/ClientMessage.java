package cachevg.connection.tcp.server.message;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Objects;

public record ClientMessage(SocketAddress clientAddress, byte[] message) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientMessage that = (ClientMessage) o;
        return Objects.equals(clientAddress, that.clientAddress) && Arrays.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(clientAddress);
        result = 31 * result + Arrays.hashCode(message);
        return result;
    }

    @Override
    public String toString() {
        return "ClientMessage{" + "clientAddress=" + clientAddress +
               '}';
    }
}
