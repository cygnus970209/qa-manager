import type {
  GithubAppStatus,
  GithubCommit,
  GithubManifestResponse,
  GithubRepo,
} from '~/types/api'

export function useGithub() {
  const api = useApi()
  return {
    appStatus: () =>
      api<GithubAppStatus>('/api/github/app'),
    /** 응답의 targetUrl/manifest 는 hidden form POST 로 GitHub 에 제출해야 한다 (fetch 불가). */
    createManifest: (organization: string | null, baseUrl: string) =>
      api<GithubManifestResponse>('/api/github/app/manifest', {
        method: 'POST',
        body: { organization, baseUrl },
      }),
    /** GitHub 이 앱 생성 후 리다이렉트로 전달한 code 를 자격증명으로 교환. */
    convert: (code: string) =>
      api<GithubAppStatus>('/api/github/app/conversion', { method: 'POST', body: { code } }),
    removeApp: () =>
      api('/api/github/app', { method: 'DELETE' }),
    listRepos: () =>
      api<GithubRepo[]>('/api/github/repos'),
    qaCommits: (qaId: number) =>
      api<GithubCommit[]>(`/api/github/qa/${qaId}/commits`),
  }
}
