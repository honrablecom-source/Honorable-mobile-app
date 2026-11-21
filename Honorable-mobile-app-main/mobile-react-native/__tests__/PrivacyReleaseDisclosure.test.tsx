import React from 'react';
import { Alert, Modal, Text } from 'react-native';
import Renderer, { act } from 'react-test-renderer';
import { AccountGate } from '../src/auth/AccountGate';
import { BetaFeedbackScreen } from '../src/screens/BetaFeedbackScreen';
import { PrivacyDataScreen } from '../src/screens/PrivacyDataScreen';

const mockNavigate = jest.fn();
const mockSessionAction = jest.fn().mockResolvedValue({});
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ navigate: mockNavigate }),
  useFocusEffect: (effect: () => void) => require('react').useEffect(effect, [effect]),
}));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: require('react-native').View,
}));
jest.mock('../src/design-system/WorkspaceHeader', () => ({
  WorkspaceHeader: () => null,
}));
jest.mock('../src/passes/MemoryPassContext', () => ({
  useMemoryPass: () => ({ signedIn: false, status: 'welcome', googleConfigured: true }),
}));
jest.mock('lucide-react-native', () => ({
  ChevronRight: require('react-native').View,
  ShieldCheck: require('react-native').View,
}));
jest.mock('../src/native/HonorableNative', () => ({
  honorableNative: {
    accountSessionAction: (...args: unknown[]) => mockSessionAction(...args),
    getImprovementProgramState: jest.fn().mockResolvedValue({ enabled: false }),
  },
}));

beforeEach(() => jest.clearAllMocks());
afterEach(() => jest.restoreAllMocks());

function pressable(tree: Renderer.ReactTestRenderer, label: string) {
  return tree.root.findAll(node => node.props.accessibilityLabel === label &&
    typeof node.props.onPress === 'function')[0];
}

test('Privacy & Data links to the notice and the account request form', async () => {
  let tree!: Renderer.ReactTestRenderer;
  await act(async () => { tree = Renderer.create(<PrivacyDataScreen />); });
  act(() => pressable(tree, 'Privacy information').props.onPress());
  act(() => pressable(tree, 'Account data requests').props.onPress());
  expect(mockNavigate.mock.calls).toEqual([['PrivacyPolicy'], ['BetaFeedback']]);
  act(() => tree.unmount());
});

test('the welcome screen opens the privacy notice before sign-in', async () => {
  let tree!: Renderer.ReactTestRenderer;
  await act(async () => {
    tree = Renderer.create(<AccountGate><Text>Signed-in workspace</Text></AccountGate>);
  });
  act(() => pressable(tree, 'Privacy notice').props.onPress());
  expect(tree.root.findByType(Modal).props.visible).toBe(true);
  expect(tree.root.findAllByType(Text).some(node =>
    node.props.children === 'Your library and edits',
  )).toBe(true);
  expect(mockSessionAction).not.toHaveBeenCalled();
  act(() => tree.root.findByType(Modal).props.onRequestClose());
  expect(tree.root.findByType(Modal).props.visible).toBe(false);
  act(() => tree.unmount());
});

test('deletion sends the existing request only after confirmation', async () => {
  const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
  let tree!: Renderer.ReactTestRenderer;
  await act(async () => { tree = Renderer.create(<BetaFeedbackScreen />); });
  mockSessionAction.mockClear();
  act(() => pressable(tree, 'Request account deletion').props.onPress());
  expect(alert.mock.calls[0][0]).toBe('Request account deletion?');
  expect(mockSessionAction).not.toHaveBeenCalled();
  const submit = alert.mock.calls[0][2]?.find(button => button.text === 'Submit');
  expect(submit?.onPress).toBeDefined();
  await act(async () => submit?.onPress?.());
  expect(mockSessionAction).toHaveBeenCalledWith(
    '/v1/beta/request', '{"type":"ACCOUNT_DELETION"}',
  );
  expect(tree.root.findAllByType(Text).some(node =>
    node.props.children === 'Account deletion request received for review.',
  )).toBe(true);
  act(() => tree.unmount());
});
