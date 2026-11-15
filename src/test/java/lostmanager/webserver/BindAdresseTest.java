package lostmanager.webserver;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BindAdresseTest {

	@Test
	@DisplayName("Der Webserver lauscht nur auf der Schleife, nicht im Heimnetz")
	void lauschtNurAufDerSchleife() throws Exception {
		Assumptions.assumeTrue(System.getenv("LOSTMANAGER_API_BIND") == null,
				"LOSTMANAGER_API_BIND ist gesetzt, die Vorgabe laesst sich nicht pruefen");
		InetAddress fremd = null;
		for (final NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
			if (!ni.isUp() || ni.isLoopback()) {
				continue;
			}
			for (final InetAddress a : Collections.list(ni.getInetAddresses())) {
				if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
					fremd = a;
				}
			}
		}
		Assumptions.assumeTrue(fremd != null, "keine zweite Netzadresse auf dieser Maschine");

		final int port;
		try (ServerSocket frei = new ServerSocket(0)) {
			port = frei.getLocalPort();
		}
		final JsonUploadServer server = new JsonUploadServer(port);
		server.start();
		try {
			// Ueber die Schleife antwortet er ...
			try (Socket s = new Socket()) {
				s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
				assertTrue(s.isConnected());
			}
			// ... ueber die Netzadresse derselben Maschine nimmt niemand ab.
			final InetAddress ziel = fremd;
			assertThrows(ConnectException.class, () -> {
				try (Socket s = new Socket()) {
					s.connect(new InetSocketAddress(ziel, port), 2000);
				}
			}, "Der Webserver war ueber " + fremd.getHostAddress() + " erreichbar");
		} finally {
			server.stop();
		}
	}
}
