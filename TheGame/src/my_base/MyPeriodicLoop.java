package my_base;

import base.PeriodicLoop;

public class MyPeriodicLoop extends PeriodicLoop {

	private AppContent content = App.content();
	private int step = 0;

	@Override
	public void execute() {
		super.execute();
		// מפעיל את ה-AI של Urban Strike בכל פעימה
		App.content().urbanStrikeBackend().periodicAiUpdate();
	}
}
