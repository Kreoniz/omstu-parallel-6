package demo.app;

import demo.base.Base;
import demo.base.Writable;

public class Document extends Base<String> implements Writable {
    String text = "class False extends Wrong {}";
    Runnable action = new Runnable() {
        @Override public void run() {}
    };
    public static class Nested extends demo.other.Base {}
}
