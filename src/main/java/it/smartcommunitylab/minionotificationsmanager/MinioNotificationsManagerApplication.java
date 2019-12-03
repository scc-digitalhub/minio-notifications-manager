package it.smartcommunitylab.minionotificationsmanager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import it.smartcommunitylab.minionotificationsmanager.common.SystemException;
import it.smartcommunitylab.minionotificationsmanager.service.NotificationService;

@SpringBootApplication
public class MinioNotificationsManagerApplication {

    @Autowired
    NotificationService service;

    @Value("${startup.sync.export.enable}")
    private boolean syncExport;

    @Value("${startup.sync.export.clear}")
    private boolean syncExportClear;

    @Value("${startup.sync.import.enable}")
    private boolean syncImport;

    @Value("${startup.sync.import.clear}")
    private boolean syncImportClear;

    @Value("${startup.sync.halt}")
    private boolean syncHaltOnErrors;

    public static void main(String[] args) {
        SpringApplication.run(MinioNotificationsManagerApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {
            // sanity check, if we export + import disable clear
            if (syncExport && syncImport) {
                // disable minio clear
                syncExportClear = false;
                // leave enabled local clear
                // since we execute *after* export we will
                // clear all local which are not successfully exported to minio
            }

            // check if sync requested
            if (syncExport) {
                try {
                    service.syncToMinio(syncExportClear);
                } catch (SystemException ex) {
                    if (syncHaltOnErrors) {
                        // stop
                        throw ex;
                    }
                }
            }

            if (syncImport) {
                try {
                    service.syncFromMinio(syncImportClear);
                } catch (SystemException ex) {
                    if (syncHaltOnErrors) {
                        // stop
                        throw ex;
                    }
                }
            }

            printBanner();
        };
    }

    public void printBanner() {
        System.out.println("======================================");
        System.out.println(" ____                _                ");
        System.out.println("|  _ \\ ___  __ _  __| |_   _          ");
        System.out.println("| |_) / _ \\/ _` |/ _` | | | |         ");
        System.out.println("|  _ <  __/ (_| | (_| | |_| |_        ");
        System.out.println("|_| \\_\\___|\\__,_|\\__,_|\\__, (_)       ");
        System.out.println(" :minio notifications  |___/          ");
        System.out.println("======================================");
    }
}
