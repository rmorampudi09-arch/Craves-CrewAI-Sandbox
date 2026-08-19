import React from 'react';
import {SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, View} from 'react-native';
import {mobileEnvironment} from './config/env';
import {getFirebaseBootstrapState} from './services/firebase/firebase';

const App = (): React.JSX.Element => {
  const firebase = getFirebaseBootstrapState();

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" />
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>Craves Mobile Bootstrap</Text>
        <Text style={styles.subtitle}>React Native shell for production-scope alignment.</Text>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>Environment</Text>
          <Text style={styles.cardLine}>Name: {mobileEnvironment.environmentName}</Text>
          <Text style={styles.cardLine}>API Base URL: {mobileEnvironment.apiBaseUrl}</Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>Firebase integration</Text>
          <Text style={styles.cardLine}>
            Status: {firebase.configured ? 'Configured via runtime values' : 'Pending secure wiring'}
          </Text>
          <Text style={styles.cardLine}>
            Project: {firebase.projectId ?? 'Not configured'}
          </Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>API integration</Text>
          <Text style={styles.cardLine}>Typed API client scaffold is ready for auth and BFF calls.</Text>
          <Text style={styles.cardLine}>No secrets or signing material are committed.</Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>Release posture</Text>
          <Text style={styles.cardLine}>App Store: PENDING MANUAL ACTION</Text>
          <Text style={styles.cardLine}>Google Play: PENDING MANUAL ACTION</Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#fff8f2',
  },
  container: {
    padding: 24,
    gap: 16,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#2d1b12',
  },
  subtitle: {
    fontSize: 16,
    color: '#6e4d3d',
    marginBottom: 8,
  },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#eedfd4',
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 8,
    color: '#2d1b12',
  },
  cardLine: {
    fontSize: 14,
    color: '#4b3428',
    marginBottom: 4,
  },
});

export default App;
