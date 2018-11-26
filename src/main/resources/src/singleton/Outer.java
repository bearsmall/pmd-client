package src.singleton;

final class Outer {
  private static final Outer INSTANCE = new Outer();
  private Outer() {}
  static Outer getInstance() { return INSTANCE; }

  static class Factory {
    private static final Factory FACTORY = new Factory();
    private Factory() { }
    static Factory getInstance() { return FACTORY; }
  }
}