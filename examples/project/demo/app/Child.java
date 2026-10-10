package demo.app;

public class Child extends Outer<String>.Inner<Integer> {
    public Child(Outer<String> outer) {
        outer.super();
    }
}
