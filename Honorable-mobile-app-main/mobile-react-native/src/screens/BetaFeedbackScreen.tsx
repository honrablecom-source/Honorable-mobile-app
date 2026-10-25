import { WorkspaceHeader } from '../design-system/WorkspaceHeader';
import { SafeAreaView } from 'react-native-safe-area-context';
import { colors } from '../design-system/tokens';
import React, { useEffect, useState, useRef } from 'react';
import {
  Alert,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  Switch,
  StyleSheet,
  Platform,
  Image,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { honorableNative } from '../native/HonorableNative';
type BetaConfig = {
  tester?: { status: string };
  release?: {
    version: string;
    updateLevel: string;
    updateNeeded: boolean;
    testerNotes: string;
  };
  knownIssues?: { text: string }[];
};
const categories = [
  'BUG',
  'SEARCH RESULT',
  'VIDEO SEARCH',
  'EDITOR',
  'PASS/USAGE',
  'STUDIO',
  'PERFORMANCE',
  'UI',
  'OTHER',
];
const outcomes = [
  'CORRECT',
  'WRONG',
  'NOT_FOUND',
  'TIMESTAMP_WRONG',
  'TOO_SLOW',
];
const request = async (route: string, body?: object) =>
  honorableNative.accountSessionAction(
    '/v1/beta/' + route,
    body ? JSON.stringify(body) : null,
  ) as unknown as Promise<any>;
export function BetaFeedbackScreen() {
  const navigation = useNavigation();
  const [config, setConfig] = useState<BetaConfig | null>(null),
    [category, setCategory] = useState('BUG'),
    [title, setTitle] = useState(''),
    [description, setDescription] = useState(''),
    [consent, setConsent] = useState(false),
    [outcome, setOutcome] = useState('WRONG'),
    [screenshot, setScreenshot] = useState<string | null>(null),
    [attachmentConsent, setAttachmentConsent] = useState(false),
    [diagnostic, setDiagnostic] = useState(false),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState('');
  const eventId = useRef('');
  useEffect(() => {
    request('config')
      .then(setConfig)
      .catch(() =>
        setMessage(
          'Beta status unavailable. Connect to your configured account service.',
        ),
      );
  }, []);
  async function submit() {
    setBusy(true);
    try {
      if (!eventId.current)
        eventId.current = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(
          /[xy]/g,
          c => {
            const r = Math.floor(Math.random() * 16);
            return (c === 'x' ? r : (r & 3) | 8).toString(16);
          },
        );
      const result = await request('feedback', {
        eventId: eventId.current,
        category,
        title,
        description,
        context: {
          screen: 'BetaFeedback',
          platform: Platform.OS === 'android' ? 'ANDROID' : 'IOS',
        },
        ...(consent ? { diagnosticConsent: true, search: { outcome } } : {}),
      });
      if (screenshot) {
        if (!attachmentConsent) throw Error('Consent required');
        await request('attachment', {
          reportId: result.id,
          kind: 'SCREENSHOT',
          consent: true,
          base64: screenshot,
        });
      }
      if (diagnostic)
        await request('attachment', {
          reportId: result.id,
          kind: 'DIAGNOSTIC',
          consent: true,
          diagnostic: {
            screen: 'BetaFeedback',
            platform: Platform.OS === 'android' ? 'ANDROID' : 'IOS',
          },
        });
      setScreenshot(null);
      setAttachmentConsent(false);
      setMessage('Feedback received: ' + result.id);
      eventId.current = '';
      setTitle('');
      setDescription('');
    } catch {
      setMessage(
        'Feedback could not be sent. Check your connection and active beta membership; your text remains here.',
      );
    } finally {
      setBusy(false);
    }
  }
  function privacy(type: string) {
    Alert.alert(
      'Send account request',
      type === 'ANALYTICS_OPT_OUT'
        ? 'Stop optional analytics collection?'
        : 'Submit this request for review? Beta removal ends beta access.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Submit',
          onPress: () =>
            request('request', { type })
              .then(() =>
                setMessage(
                  'Request recorded. Purchase records are handled separately.',
                ),
              )
              .catch(() => setMessage('Request could not be sent.')),
        },
      ],
    );
  }
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.canvas }}>
      <WorkspaceHeader
        title="Beta feedback"
        section="Noble / support"
        onBack={() => navigation.goBack()}
      />
      <ScrollView contentContainerStyle={styles.page}>
        <Text style={styles.text}>
          Share a short report. No media, search text, project files or
          credentials are attached automatically. Only include information you
          intend to share with the internal team.
        </Text>
        {config?.release && (
          <Text style={styles.text}>
            {config.release.updateNeeded
              ? config.release.updateLevel + ' update available: '
              : 'Current release: '}
            {config.release.version}
            {'\n'}
            {config.release.testerNotes}
          </Text>
        )}
        {config?.knownIssues?.map((issue, i) => (
          <Text key={i} style={styles.text}>
            {issue.text}
          </Text>
        ))}
        <Text style={styles.text}>Category: {category}</Text>
        {categories.map(c => (
          <Pressable key={c} onPress={() => setCategory(c)}>
            <Text style={styles.choice}>
              {c === category ? '● ' : ''}
              {c}
            </Text>
          </Pressable>
        ))}
        <TextInput
          accessibilityLabel="Feedback title"
          placeholder="Short title"
          placeholderTextColor={colors.textSecondary}
          value={title}
          onChangeText={setTitle}
          maxLength={120}
          style={styles.input}
        />
        <TextInput
          accessibilityLabel="Feedback description"
          placeholder="What happened? What did you expect?"
          placeholderTextColor={colors.textSecondary}
          multiline
          value={description}
          onChangeText={setDescription}
          maxLength={4000}
          style={styles.input}
        />
        <Text style={styles.text}>
          Include an explicit search-quality assessment (optional)
        </Text>
        <Switch
          trackColor={{
            false: colors.surfaceSelected,
            true: colors.textPrimary,
          }}
          thumbColor={colors.canvas}
          value={consent}
          onValueChange={setConsent}
        />
        {consent &&
          outcomes.map(o => (
            <Pressable key={o} onPress={() => setOutcome(o)}>
              <Text style={styles.choice}>
                {outcome === o ? '● ' : ''}
                {o}
              </Text>
            </Pressable>
          ))}
        <Pressable
          onPress={() =>
            Alert.alert(
              'Attach a screenshot?',
              'It may contain private media. Choose only an image you consent to share with the internal team. Nothing is captured automatically.',
              [
                { text: 'Cancel', style: 'cancel' },
                {
                  text: 'Choose screenshot',
                  onPress: () =>
                    honorableNative
                      .pickBetaScreenshot()
                      .then(value => {
                        setScreenshot(value?.base64 || null);
                        setAttachmentConsent(false);
                      })
                      .catch(() =>
                        setMessage('Screenshot could not be opened.'),
                      ),
                },
              ],
            )
          }
        >
          <Text style={styles.choice}>Choose optional screenshot</Text>
        </Pressable>
        {screenshot && (
          <>
            <Image
              source={{ uri: 'data:image/png;base64,' + screenshot }}
              style={{ height: 160 }}
              resizeMode="contain"
            />
            <Text style={styles.text}>
              I consent to send this selected image, including any private
              content it contains.
            </Text>
            <Switch
              value={attachmentConsent}
              onValueChange={setAttachmentConsent}
            />
            <Pressable onPress={() => setScreenshot(null)}>
              <Text style={styles.choice}>Remove screenshot</Text>
            </Pressable>
          </>
        )}
        <Text style={styles.text}>
          Attach safe diagnostic JSON (platform and screen only; no files or
          queries)
        </Text>
        <Switch
          trackColor={{
            false: colors.surfaceSelected,
            true: colors.textPrimary,
          }}
          thumbColor={colors.canvas}
          value={diagnostic}
          onValueChange={setDiagnostic}
        />
        <Pressable
          disabled={
            busy ||
            (!!screenshot && !attachmentConsent) ||
            !title.trim() ||
            !description.trim() ||
            config?.tester?.status !== 'ACTIVE'
          }
          onPress={submit}
        >
          <Text style={styles.choice}>
            {busy ? 'Sending…' : 'Send feedback'}
          </Text>
        </Pressable>
        <Text accessibilityLiveRegion="polite" style={styles.text}>
          {message}
        </Text>
        {['ACCOUNT_DELETION', 'BETA_REMOVAL', 'ANALYTICS_OPT_OUT'].map(type => (
          <Pressable key={type} onPress={() => privacy(type)}>
            <Text style={styles.choice}>{type.replaceAll('_', ' ')}</Text>
          </Pressable>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}
const styles = StyleSheet.create({
  page: {
    padding: 24,
    paddingTop: 20,
    paddingBottom: 80,
    backgroundColor: colors.canvas,
    gap: 12,
  },
  title: { color: colors.textPrimary, fontSize: 28, fontWeight: '700' },
  text: { color: colors.textSecondary, lineHeight: 21 },
  choice: {
    minHeight: 48,
    textAlignVertical: 'center',
    borderBottomWidth: 1,
    borderBottomColor: colors.surfaceSelected,
    color: colors.textPrimary,
    paddingVertical: 8,
  },
  input: {
    color: colors.textPrimary,
    backgroundColor: colors.canvas,
    borderWidth: 1,
    borderColor: colors.border,
    minHeight: 48,
    padding: 12,
    borderRadius: 8,
  },
});
