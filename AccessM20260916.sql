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
  `apellido` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `horas_jornada_base` int NOT NULL,
  `legajo_reloj` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `nombre` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `telefono` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sector_id` bigint NOT NULL,
  `sucursal` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `empresa_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_legajo_reloj` (`legajo_reloj`),
  KEY `FK_empleado_sector` (`sector_id`),
  CONSTRAINT `FK_empleado_sector` FOREIGN KEY (`sector_id`) REFERENCES `sectores` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `empleados`
--

LOCK TABLES `empleados` WRITE;
/*!40000 ALTER TABLE `empleados` DISABLE KEYS */;
INSERT INTO `empleados` VALUES (1,'Larraya',8,'101','Gaston',NULL,1,'',0),(2,'Perez',8,'102','Juan','56465465',1,'Planta Central',1),(3,'Gomez',8,'103','Carlos',NULL,3,'',0),(4,'lopez',8,'104','lucas','65654842',2,'Planta Central',1);
/*!40000 ALTER TABLE `empleados` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `empresas`
--

DROP TABLE IF EXISTS `empresas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `empresas` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nombre` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_empresa_nombre` (`nombre`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `empresas`
--

LOCK TABLES `empresas` WRITE;
/*!40000 ALTER TABLE `empresas` DISABLE KEYS */;
INSERT INTO `empresas` VALUES (3,'COOP-HER'),(2,'FluxTech Admin'),(1,'Kiosco De Lourdes'),(4,'prueba');
/*!40000 ALTER TABLE `empresas` ENABLE KEYS */;
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
  `legajo_reloj` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `modo_verificacion` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `idEmpresa` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `fichadas`
--

LOCK TABLES `fichadas` WRITE;
/*!40000 ALTER TABLE `fichadas` DISABLE KEYS */;
INSERT INTO `fichadas` VALUES (1,'2026-06-08 10:55:00.000000','101','Huella',0),(2,'2026-06-08 19:00:00.000000','101','Huella',0),(3,'2026-06-08 11:00:00.000000','102','Huella',0),(4,'2026-06-08 22:30:00.000000','102','Huella',0),(5,'2026-06-08 10:58:00.000000','103','Huella',0);
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
  `id_empleado_reloj` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
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
-- Table structure for table `licencias`
--

DROP TABLE IF EXISTS `licencias`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `licencias` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `empresa_id` bigint NOT NULL,
  `fecha_fin` date NOT NULL,
  `fecha_inicio` date NOT NULL,
  `observaciones` text,
  `tipo_licencia` varchar(255) NOT NULL,
  `empleado_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKow34okd5yi66yknqmfkoyw4hm` (`empleado_id`),
  CONSTRAINT `FKow34okd5yi66yknqmfkoyw4hm` FOREIGN KEY (`empleado_id`) REFERENCES `empleados` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `licencias`
--

LOCK TABLES `licencias` WRITE;
/*!40000 ALTER TABLE `licencias` DISABLE KEYS */;
INSERT INTO `licencias` VALUES (1,1,'2026-09-23','2026-09-01','vacaciones ','Vacaciones',1);
/*!40000 ALTER TABLE `licencias` ENABLE KEYS */;
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
  `observaciones` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
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

--
-- Table structure for table `sectores`
--

DROP TABLE IF EXISTS `sectores`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sectores` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nombre` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sucursal_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_sector_sucursal` (`sucursal_id`),
  CONSTRAINT `FK_sector_sucursal` FOREIGN KEY (`sucursal_id`) REFERENCES `sucursal` (`idsucursal`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sectores`
--

LOCK TABLES `sectores` WRITE;
/*!40000 ALTER TABLE `sectores` DISABLE KEYS */;
INSERT INTO `sectores` VALUES (1,'Telecomunicaciones',1),(2,'Sistemas',1),(3,'Telecomunicaciones',2),(4,'administracion',3),(5,'logistica',3),(6,'administracion',4),(7,'deposito',4);
/*!40000 ALTER TABLE `sectores` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sucursal`
--

DROP TABLE IF EXISTS `sucursal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sucursal` (
  `idsucursal` bigint NOT NULL AUTO_INCREMENT,
  `nombre` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `empresa_id` bigint NOT NULL,
  PRIMARY KEY (`idsucursal`),
  KEY `FK_sucursal_empresa` (`empresa_id`),
  CONSTRAINT `FK_sucursal_empresa` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sucursal`
--

LOCK TABLES `sucursal` WRITE;
/*!40000 ALTER TABLE `sucursal` DISABLE KEYS */;
INSERT INTO `sucursal` VALUES (1,'Planta Central',1),(2,'Sucursal B',1),(3,'centro',4),(4,'interior',4);
/*!40000 ALTER TABLE `sucursal` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `usuarios`
--

DROP TABLE IF EXISTS `usuarios`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `usuarios` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `password` varchar(255) NOT NULL,
  `rol` varchar(255) NOT NULL,
  `username` varchar(255) NOT NULL,
  `empresa_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKm2dvbwfge291euvmk6vkkocao` (`username`),
  KEY `FK9v93lqnass5yqhhsyprr9fdv2` (`empresa_id`),
  CONSTRAINT `FK9v93lqnass5yqhhsyprr9fdv2` FOREIGN KEY (`empresa_id`) REFERENCES `empresas` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `usuarios`
--

LOCK TABLES `usuarios` WRITE;
/*!40000 ALTER TABLE `usuarios` DISABLE KEYS */;
INSERT INTO `usuarios` VALUES (1,'$2a$10$dASibmQC1qJ6wOJPPwpBjeV/vDGbgmaYAlLZgEH8Fa7bxWmmoD8wS','ADMIN','admin',1),(2,'$2a$10$5nc9NRdBmTBV9BIjguwG4ukwCNctZ/YsHPNg0ukTR4C3t79i7gEK6','ROLE_SUPERADMIN','fluxtech',2),(4,'$2a$10$TOIdJrxfJZtowQ4P3ynDfunwsRj5pC1F5IhFO9L.PUZy4zqKphv/O','ROLE_ADMIN','prueba',4);
/*!40000 ALTER TABLE `usuarios` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-16 13:30:24
