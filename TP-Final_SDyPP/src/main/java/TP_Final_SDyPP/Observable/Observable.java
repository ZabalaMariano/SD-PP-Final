package TP_Final_SDyPP.Observable;

public interface Observable 
{
	public void addObserver(Object o);
	public void notifyObserver(int op, String log);
}

