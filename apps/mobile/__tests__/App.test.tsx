import React from 'react';
import {render} from '@testing-library/react-native';
import App from '../src/App';

describe('App', () => {
  it('renders the bootstrap shell content', () => {
    const {getByText} = render(<App />);

    expect(getByText('Craves Mobile Bootstrap')).toBeTruthy();
    expect(getByText(/React Native shell for production-scope alignment\./)).toBeTruthy();
    expect(getByText(/App Store: PENDING MANUAL ACTION/)).toBeTruthy();
    expect(getByText(/Google Play: PENDING MANUAL ACTION/)).toBeTruthy();
  });
});
