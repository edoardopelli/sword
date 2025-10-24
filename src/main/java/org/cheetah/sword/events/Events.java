package org.cheetah.sword.events;

import org.cheetah.sword.model.ConnectionConfig;
import org.cheetah.sword.model.SchemaSelection;

public final class Events {
    private Events() {}

    /** Emesso quando l'app è pronta e si può lanciare il wizard */
    public record StartWizardEvent() {}

    /** Emesso dopo che l'utente ha inserito parametri di connessione e il DB è raggiungibile */
    public record ConnectionReadyEvent(ConnectionConfig config) {}

    /** Emesso dopo che l'utente ha selezionato schema e/o catalog */
    public record SchemaChosenEvent(ConnectionConfig config, SchemaSelection selection) {}

    /** Emesso quando si deve iniziare la generazione */
    public record GenerateRequestedEvent(ConnectionConfig config, SchemaSelection selection) {}

    /** Emesso a generazione completata */
    public record GenerationCompletedEvent(int entityCount, java.nio.file.Path outputPath) {}
}