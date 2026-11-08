// Generates a random subset of the MAT, Java, OS, locale and time zone combinations for CI.
// Preview the jobs with "node .github/workflows/matrix.mjs" after "npm ci --prefix .github/workflows".
// See https://github.com/vlsi/github-actions-random-matrix
import { readFileSync } from 'fs';
import { createGitHubMatrixBuilder, setGitHubOutput } from '@vlsi/github-actions-random-matrix/github';

const { matrix } = createGitHubMatrixBuilder();
matrix.failOnUnsatisfiableFilters(true);

// The Java version each MAT needs is the execution environment the root pom sets for it: the default
// for the oldest supported MAT, the mat-latest profile for the newest one.
const pom = readFileSync(new URL('../../pom.xml', import.meta.url), 'utf8');
const [oldestMatJava, newestMatJava] = [...pom.matchAll(/<mat\.executionEnvironment>JavaSE-(\d+)</g)]
  .map(m => Number(m[1]));
if (!oldestMatJava || !newestMatJava) {
  throw new Error('pom.xml should set mat.executionEnvironment twice: by default and in the mat-latest profile');
}

matrix.addAxis({
  name: 'mat',
  title: x => x.title,
  values: [
    {value: 'oldest', title: 'oldest MAT', maven_args: '', min_java: oldestMatJava},
    {value: 'newest', title: 'newest MAT', maven_args: '-Pmat-latest', min_java: newestMatJava},
  ]
});

matrix.addAxis({
  name: 'java_version',
  title: x => 'Java ' + x,
  // No supported MAT runs on a Java older than the oldest MAT requires
  values: ['17', '21', '25'].filter(v => Number(v) >= oldestMatJava)
});

matrix.addAxis({
  name: 'java_distribution',
  values: ['corretto', 'liberica', 'microsoft', 'temurin', 'zulu']
});

matrix.addAxis({
  name: 'os',
  title: x => x.value.replace('-latest', ''),
  values: [
    {value: 'ubuntu-latest', weight: 4},
    {value: 'windows-latest', weight: 1},
  ]
});

// Turkish finds case conversions without an explicit locale, since "I" lowercases to a dotless "ı"
matrix.addAxis({
  name: 'locale',
  title: x => x.language + '_' + x.country,
  values: [
    {language: 'en', country: 'US'},
    {language: 'de', country: 'DE'},
    {language: 'tr', country: 'TR'},
  ]
});

// Pacific/Chatham has a UTC offset of +12:45, which no whole-hour arithmetic gets right
matrix.addAxis({
  name: 'tz',
  values: ['UTC', 'America/New_York', 'Pacific/Chatham']
});

matrix.constrain(['mat', 'java_version'], (mat, javaVersion) => Number(javaVersion) >= mat.min_java);

matrix.setNamePattern(['mat', 'java_version', 'java_distribution', 'os', 'locale', 'tz']);

const include = matrix.generateRows(Number(process.env.MATRIX_JOBS || 4), {
  require: [
    // The update site is built from the oldest supported MAT, on the oldest Java it supports
    {
      filter: {mat: {value: 'oldest'}, java_version: String(oldestMatJava), os: {value: 'ubuntu-latest'}},
      tag: row => { row.publish = true; },
    },
    ...matrix.allAxisValues('mat'),
    ...matrix.allAxisValues('java_version'),
    ...matrix.allAxisValues('os'),
  ],
});
if (include.length === 0) {
  throw new Error('Matrix list is empty');
}

include.sort((a, b) => a.name.localeCompare(b.name, undefined, {numeric: true}));
include.forEach(row => {
  row.runs_on = row.os.value;
  row.maven_args = row.mat.maven_args;
  row.test_jvm_args = `-Duser.language=${row.locale.language} -Duser.country=${row.locale.country} -Duser.timezone=${row.tz}`;
  row.publish = row.publish === true;
});

if (process.argv.includes('--coverage')) {
  const coverage = matrix.pairCoverageReport();
  console.log(`Pair coverage: ${coverage.covered}/${coverage.total} (${coverage.percentage}%)`);
} else {
  console.log(include);
  setGitHubOutput('matrix', {include});
}
