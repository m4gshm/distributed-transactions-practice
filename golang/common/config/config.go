package config

import (
	"fmt"
	"os"
	"strconv"

	"github.com/m4gshm/gollections/slice"
	"github.com/rs/zerolog"
)

type Config struct {
	Order   OrderConfig
	Payment PaymentConfig
	Reserve ServiceConfig
	TPC     ServiceConfig
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

type PaymentConfig struct {
	ServiceConfig
	KafkaConfig
}

type OrderConfig struct {
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
	otlpUrl := getEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "localhost:4317")
	kafka := KafkaConfig{
		Topic:   "balance",
		Servers: slice.Of("localhost:9092"),
	}
	payment := PaymentConfig{
		ServiceConfig: ServiceConfig{
			GrpcPort: getEnvInt("PAYMENTS_PORT", 9002),
			HttpPort: getEnvInt("PAYMENTS_PORT", 8002),
			OtlpUrl:  otlpUrl,
			Database: defaultDBConfig("payment"),
			LogLevel: defaultLogLevel("PAYMENTS"),
		},
		KafkaConfig: kafka,
	}
	reserve := ServiceConfig{
		GrpcPort: getEnvInt("RESERVE_PORT", 9003),
		HttpPort: getEnvInt("RESERVE_PORT", 8003),
		OtlpUrl:  otlpUrl,
		Database: defaultDBConfig("reserve"),
		LogLevel: defaultLogLevel("RESERVE"),
	}
	return &Config{
		Order: OrderConfig{
			ServiceConfig: ServiceConfig{
				GrpcPort: getEnvInt("ORDERS_PORT", 9001),
				HttpPort: getEnvInt("ORDERS_PORT", 8001),
				OtlpUrl:  otlpUrl,
				Database: defaultDBConfig("orders"),
				LogLevel: defaultLogLevel("ORDERS"),
			},
			KafkaConfig:       kafka,
			PaymentServiceURL: getEnv("ORDERS_PAYMENT_SERVICE_URL", fmt.Sprintf("localhost:%d", payment.GrpcPort)),
			ReserveServiceURL: getEnv("ORDERS_RESERVE_SERVICE_URL", fmt.Sprintf("localhost:%d", reserve.GrpcPort)),
		},
		Payment: payment,
		Reserve: reserve,
	}
}

func defaultLogLevel(prefix string) LogLevel {
	return LogLevel{
		Root: getEnvInt(prefix+"_LOG_LEVEL_ROOT", zerolog.InfoLevel),
		DB:   getEnvInt(prefix+"_LOG_LEVEL_DB", zerolog.InfoLevel),
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
