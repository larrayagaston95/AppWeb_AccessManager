-- MySQL dump 10.13  Distrib 8.0.29, for Win64 (x86_64)
--
-- Host: localhost    Database: accessmanager_db
-- ------------------------------------------------------
-- Server version	8.0.29

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `empleados`
--

DROP TABLE IF EXISTS `empleados`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `empleados` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `apellido` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `horas_jornada_base` int NOT NULL,
  `legajo_reloj` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nombre` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9bg0okemfev7hswj3mhr3yxaj` (`legajo_reloj`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `empleados`
--

LOCK TABLES `empleados` WRITE;
/*!40000 ALTER TABLE `empleados` DISABLE KEYS */;
INSERT INTO `empleados` VALUES (1,'Larraya',8,'101','Gaston'),(2,'Perez',8,'102','Juan'),(3,'Gomez',8,'103','Carlos');
/*!40000 ALTER TABLE `empleados` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `fichadas`
--

DROP TABLE IF EXISTS `fichadas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fichadas` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `fecha_hora` datetime(6) NOT NULL,
  `legajo_reloj` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `modo_verificacion` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `fichadas`
--

LOCK TABLES `fichadas` WRITE;
/*!40000 ALTER TABLE `fichadas` DISABLE KEYS */;
INSERT INTO `fichadas` VALUES (1,'2026-06-08 10:55:00.000000','101','Huella'),(2,'2026-06-08 19:00:00.000000','101','Huella'),(3,'2026-06-08 11:00:00.000000','102','Huella'),(4,'2026-06-08 22:30:00.000000','102','Huella'),(5,'2026-06-08 10:58:00.000000','103','Huella');
/*!40000 ALTER TABLE `fichadas` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `fichajes_crudos`
--

DROP TABLE IF EXISTS `fichajes_crudos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fichajes_crudos` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `empresa_id` bigint NOT NULL,
  `fecha_hora` datetime(6) NOT NULL,
  `id_empleado_reloj` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `fichajes_crudos`
--

LOCK TABLES `fichajes_crudos` WRITE;
/*!40000 ALTER TABLE `fichajes_crudos` DISABLE KEYS */;
INSERT INTO `fichajes_crudos` VALUES (1,1,'2026-06-11 11:00:00.000000','102'),(2,1,'2026-06-11 20:30:00.000000','102');
/*!40000 ALTER TABLE `fichajes_crudos` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reportes_asistencia`
--

DROP TABLE IF EXISTS `reportes_asistencia`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reportes_asistencia` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entrada` datetime(6) DEFAULT NULL,
  `fecha` date NOT NULL,
  `horas_extras` double DEFAULT NULL,
  `horas_trabajadas` double DEFAULT NULL,
  `observaciones` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `salida` datetime(6) DEFAULT NULL,
  `empleado_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKt1kutnqoyucp62lthmhho8c2p` (`empleado_id`),
  CONSTRAINT `FKt1kutnqoyucp62lthmhho8c2p` FOREIGN KEY (`empleado_id`) REFERENCES `empleados` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reportes_asistencia`
--

LOCK TABLES `reportes_asistencia` WRITE;
/*!40000 ALTER TABLE `reportes_asistencia` DISABLE KEYS */;
INSERT INTO `reportes_asistencia` VALUES (1,'2026-06-08 10:55:00.000000','2026-06-08',0.08,8.08,'Jornada Completa + Extras','2026-06-08 19:00:00.000000',1),(2,'2026-06-08 11:00:00.000000','2026-06-08',3.5,11.5,'Jornada Completa + Extras','2026-06-08 22:30:00.000000',2),(3,'2026-06-08 10:58:00.000000','2026-06-08',0,0,'Falta Fichada de Salida',NULL,3);
/*!40000 ALTER TABLE `reportes_asistencia` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-11 14:15:23
