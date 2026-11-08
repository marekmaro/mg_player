# Nazwy enumów zapisujemy w ustawieniach (np. wybrany sygnał stopera, stos ekranów),
# więc muszą zostać takie same między wersjami aplikacji.
-keepclassmembers enum io.github.marekmaro.equinox.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
