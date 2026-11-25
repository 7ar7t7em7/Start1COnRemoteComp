package aaaStart1COnRemoteComp;

/** Этапы полного цикла обмена между центральной и периферийной базами. */
public enum ExchangeStage {
    UNLOAD_FROM_CENTER("Выгрузка из ЦБ"),
    LOAD_TO_PERIPHERY("Загрузка в ПБ"),
    UNLOAD_FROM_PERIPHERY("Выгрузка из ПБ"),
    LOAD_TO_CENTER("Загрузка в ЦБ");

    private final String title;
    ExchangeStage(String title) { this.title = title; }
    public String getTitle() { return title; }
}
