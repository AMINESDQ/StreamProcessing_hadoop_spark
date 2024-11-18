package org.example;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import static org.apache.spark.sql.functions.*;

public class StreamingApp {
    public static void main(String[] args) {
        System.out.println("Application Started ...");

        // Initialize SparkSession with HDFS configuration
        SparkSession spark = SparkSession.builder()
                .appName("HospitalIncidentStreaming")
                .config("spark.hadoop.fs.defaultFS", "hdfs://namenode:8020") // Point to your HDFS Namenode
                .getOrCreate();
        spark.sparkContext().setLogLevel("ERROR");
        // Define the schema (corriger le type de "titre" et "service")
        StructType schema = new StructType(new StructField[] {
                DataTypes.createStructField("Id", DataTypes.IntegerType, true),
                DataTypes.createStructField("titre", DataTypes.StringType, true), // titre est une chaîne
                DataTypes.createStructField("description", DataTypes.StringType, true),
                DataTypes.createStructField("service", DataTypes.StringType, true), // service semble être une chaîne
                DataTypes.createStructField("date", DataTypes.StringType, true) // date est une chaîne au format yyyy-MM-dd
        });

        // Read CSV files from HDFS as a streaming source
        Dataset<Row> incidents = spark.readStream()
                .format("csv")
                .schema(schema)
                .option("header", "true")
                .load("hdfs://namenode:8020/user/hadoop/csv_data/");  // Remplacez par le chemin HDFS où les fichiers CSV sont stockés

        // Print the schema to the console to see what we are working with
        incidents.printSchema();

        // Transformation pour calculer le nombre d'incidents par service
        Dataset<Row> incidentsParService = incidents.groupBy("service").count();

        // Conversion de la colonne 'date' en type 'Date' pour extraire l'année
        Dataset<Row> incidentsParAnnee = incidents
                .withColumn("date", to_date(col("date"), "yyyy-MM-dd")) // Convertir la colonne date en type Date
                .withColumn("annee", year(col("date")))  // Extraire l'année de la date
                .groupBy("annee")  // Grouper par année
                .count()  // Compter les incidents par année
                .orderBy(col("count").desc())  // Trier par le nombre d'incidents de manière décroissante
                .limit(2);  // Limiter à 2 résultats pour afficher les années avec les plus d'incidents

        // Démarrer les flux d'écriture pour les résultats
        try {
            // Flux principal : incidents par service
            incidentsParService.writeStream()
                    .format("console")
                    .outputMode("complete")  // Mode complet pour les agrégations
                    .queryName("IncidentsParService")  // Nommez le streaming
                    .start();

             // Flux pour incidents par année
            incidentsParAnnee.writeStream()
                    .format("console")
                    .outputMode("complete")  // Mode complet pour les agrégations
                    .queryName("IncidentsParAnnee")  // Nommez le streaming
                    .start();


            // Attente de la fin des flux
            spark.streams().awaitAnyTermination();  // Attendre l'arrêt de tous les flux
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Application Completed.");
    }
}
