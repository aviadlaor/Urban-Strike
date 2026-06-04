package my_base;
import ai.control.UrbanStrikeBackend;

/*
 * This class should hold the content of the system, i.e., all elements that are
 * related to the essence of the system.
 *
 */
public class AppContent {
	private UrbanStrikeBackend urbanStrikeBackend;

	public void initContent() {
		// Urban Strike content initializes lazily via urbanStrikeBackend()
	}

	public UrbanStrikeBackend urbanStrikeBackend() {
		if (urbanStrikeBackend == null) {
			urbanStrikeBackend = new UrbanStrikeBackend();
		}
		return urbanStrikeBackend;
	}
}


