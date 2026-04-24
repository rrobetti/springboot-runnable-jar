package com.example.export.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration for the export job.
 *
 * <p>Properties are bound from the {@code app} prefix in {@code application.properties}.
 * <ul>
 *   <li>{@code app.output.directory} – directory where the output file will be written</li>
 *   <li>{@code app.output.filename}  – filename template; use {@code {param}} as a placeholder
 *       for the runtime parameter, e.g. {@code export_{param}.csv}</li>
 *   <li>{@code app.filter.status}    – value used to filter rows from the {@code person} table</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Output output = new Output();
    private Filter filter = new Filter();

    public Output getOutput() {
        return output;
    }

    public void setOutput(Output output) {
        this.output = output;
    }

    public Filter getFilter() {
        return filter;
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    public static class Output {

        /** Base directory for the generated file. */
        private String directory = "/tmp/output";

        /**
         * Filename template. The literal {@code {param}} is replaced at runtime with
         * the value supplied on the command line.
         */
        private String filename = "export_{param}.dat";
        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }
    }

    public static class Filter {

        /** Status value used to filter the {@code person} table. */
        private String status = "ACTIVE";

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
