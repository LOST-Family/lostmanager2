package lostmanager.apiutil;

/** Die CoC-API hat keine Antwort geliefert (Ausfall, Wartung) – nicht zu verwechseln mit "gibt es nicht". */
public class ApiNichtErreichbarException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public ApiNichtErreichbarException(String nachricht) {
		super(nachricht);
	}
}
