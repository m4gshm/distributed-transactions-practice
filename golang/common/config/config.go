package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"

	"github.com/rs/zerolog"
)

type Config struct {
	Orders   OrdersConfig
	Payments PaymentsConfig
	Reserve  ServiceConfig
	TPC      ServiceConfig
}

type DatabaseConfig struct {
	Host     string
	Port     int
	User     string
	Password string
	DBName   string
	SSLMode  string
}

type KafkaConfig struct {
	Topic   string
	Servers []string
}

type PaymentsConfig struct {
	ServiceConfig
	KafkaConfig
}

type OrdersConfig struct {
	ServiceConfig
	KafkaConfig
	PaymentServiceURL string
	ReserveServiceURL string
}

type ServiceConfig struct {
	GrpcPort int
	HttpPort int
	Database DatabaseConfig
	OtlpUrl  string
	LogLevel LogLevel
}

type LogLevel struct {
	Root zerolog.Level
	DB   zerolog.Level
}

func Load() *Config {
	otlpUrl := getEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317")
	kafka := KafkaConfig{
		Topic:   "balance",
		Servers: strings.Split(getEnv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"), ","),
	}
	payment := PaymentsConfig{
		ServiceConfig: ServiceConfig{
			GrpcPort: getEnvInt("GRPC_PORT", 9002),
			HttpPort: getEnvInt("HTTP_PORT", 8002),
			OtlpUrl:  otlpUrl,
			Database: defaultDBConfig("go_payment"),
			LogLevel: defaultLogLevel(),
		},
		KafkaConfig: kafka,
	}
	reserve := ServiceConfig{
		GrpcPort: getEnvInt("GRPC_PORT", 9003),
		HttpPort: getEnvInt("HTTP_PORT", 8003),
		OtlpUrl:  otlpUrl,
		Database: defaultDBConfig("go_reserve"),
		LogLevel: defaultLogLevel(),
	}
	return &Config{
		Orders: OrdersConfig{
			ServiceConfig: ServiceConfig{
				GrpcPort: getEnvInt("GRPC_PORT", 9001),
				HttpPort: getEnvInt("HTTP_PORT", 8001),
				OtlpUrl:  otlpUrl,
				Database: defaultDBConfig("go_orders"),
				LogLevel: defaultLogLevel(),
			},
			KafkaConfig:       kafka,
			PaymentServiceURL: getEnv("SERVICE_PAYMENTS_ADDRESS", fmt.Sprintf("localhost:%d", payment.GrpcPort)),
			ReserveServiceURL: getEnv("SERVICE_RESERVE_ADDRESS", fmt.Sprintf("localhost:%d", reserve.GrpcPort)),
		},
		Payments: payment,
		Reserve:  reserve,
	}
}

func defaultLogLevel() LogLevel {
	return LogLevel{
		Root: getEnvInt("LOG_LEVEL_ROOT", zerolog.InfoLevel),
		DB:   getEnvInt("LOG_LEVEL_DB", zerolog.WarnLevel),
	}
}

func defaultDBConfig(dbNameDefault string) DatabaseConfig {
	return DatabaseConfig{
		Host:     getEnv("DB_HOST", "localhost"),
		Port:     getEnvInt("DB_PORT", 5000),
		User:     getEnv("DB_USER", "postgres"),
		Password: getEnv("DB_PASSWORD", "postgres"),
		DBName:   getEnv("DB_NAME", dbNameDefault),
		SSLMode:  getEnv("DB_SSL_MODE", "disable"),
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func getEnvInt[I ~int | ~int8](key string, defaultValue I) I {
	if value := os.Getenv(key); value != "" {
		if intValue, err := strconv.Atoi(value); err == nil {
			return I(intValue)
		}
	}
	return defaultValue
}
