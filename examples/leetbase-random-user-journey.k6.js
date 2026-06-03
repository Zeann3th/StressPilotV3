import http from "k6/http";
import { check, group, sleep } from "k6";

export const options = {
  vus: Number(__ENV.VUS || 5),
  iterations: Number(__ENV.ITERATIONS || 25),
  thresholds: {
    http_req_failed: ["rate<0.10"],
    http_req_duration: ["p(95)<5000"],
  },
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:7554";
const PASSWORD = __ENV.SEED_PASSWORD || "password123";
const LANGUAGE = __ENV.LANGUAGE || "javascript";

const acceptedSolutions = {
  "Two Sum": "function twoSum(nums, target) { const seen = new Map(); for (let i = 0; i < nums.length; i++) { const need = target - nums[i]; if (seen.has(need)) return [seen.get(need), i]; seen.set(nums[i], i); } return []; }",
  "Valid Parentheses": "function isValid(s) { const st = []; const pairs = {')':'(', ']':'[', '}':'{'}; for (const c of s) { if ('([{'.includes(c)) st.push(c); else if (st.pop() !== pairs[c]) return false; } return st.length === 0; }",
  "Merge Two Sorted Arrays": "function mergeSorted(a, b) { const out = []; let i = 0, j = 0; while (i < a.length || j < b.length) { if (j >= b.length || (i < a.length && a[i] <= b[j])) out.push(a[i++]); else out.push(b[j++]); } return out; }",
  "Maximum Subarray": "function maxSubArray(nums) { let best = nums[0], cur = nums[0]; for (let i = 1; i < nums.length; i++) { cur = Math.max(nums[i], cur + nums[i]); best = Math.max(best, cur); } return best; }",
  "Climbing Stairs": "function climbStairs(n) { let a = 1, b = 1; for (let i = 0; i < n; i++) [a, b] = [b, a + b]; return a; }",
  "Binary Search": "function search(nums, target) { let l = 0, r = nums.length - 1; while (l <= r) { const m = (l + r) >> 1; if (nums[m] === target) return m; if (nums[m] < target) l = m + 1; else r = m - 1; } return -1; }",
  "Rotate Array": "function rotate(nums, k) { k %= nums.length; return nums.slice(-k).concat(nums.slice(0, -k)); }",
  "Palindrome Number": "function isPalindrome(x) { const s = String(x); return s === s.split('').reverse().join(''); }",
  "Reverse String": "function reverseString(s) { return s.split('').reverse().join(''); }",
  "First Unique Character": "function firstUniqChar(s) { const counts = {}; for (const c of s) counts[c] = (counts[c] || 0) + 1; for (let i = 0; i < s.length; i++) if (counts[s[i]] === 1) return i; return -1; }",
  "Contains Duplicate": "function containsDuplicate(nums) { return new Set(nums).size !== nums.length; }",
  "Move Zeroes": "function moveZeroes(nums) { const nz = nums.filter(n => n !== 0); return nz.concat(Array(nums.length - nz.length).fill(0)); }",
  "Best Time to Buy and Sell Stock": "function maxProfit(prices) { let min = Infinity, best = 0; for (const p of prices) { min = Math.min(min, p); best = Math.max(best, p - min); } return best; }",
  "Single Number": "function singleNumber(nums) { return nums.reduce((a, b) => a ^ b, 0); }",
  "Majority Element": "function majorityElement(nums) { let cand, count = 0; for (const n of nums) { if (count === 0) cand = n; count += n === cand ? 1 : -1; } return cand; }",
  "Fizz Buzz": "function fizzBuzz(n) { return Array.from({ length: n }, (_, i) => { const x = i + 1; return x % 15 === 0 ? 'FizzBuzz' : x % 3 === 0 ? 'Fizz' : x % 5 === 0 ? 'Buzz' : String(x); }); }",
  "Missing Number": "function missingNumber(nums) { return nums.length * (nums.length + 1) / 2 - nums.reduce((a, b) => a + b, 0); }",
  "Plus One": "function plusOne(digits) { const out = digits.slice(); for (let i = out.length - 1; i >= 0; i--) { if (out[i] < 9) { out[i]++; return out; } out[i] = 0; } out.unshift(1); return out; }",
  "Group Anagrams": "function groupAnagrams(strs) { const m = new Map(); for (const s of strs) { const k = s.split('').sort().join(''); if (!m.has(k)) m.set(k, []); m.get(k).push(s); } return [...m.values()]; }",
  "Product of Array Except Self": "function productExceptSelf(nums) { const out = Array(nums.length).fill(1); let p = 1; for (let i = 0; i < nums.length; i++) { out[i] = p; p *= nums[i]; } p = 1; for (let i = nums.length - 1; i >= 0; i--) { out[i] *= p; p *= nums[i]; } return out; }",
};

const names = ["Alex Kim", "Minh Tran", "Jordan Lee", "Taylor Nguyen", "Sam Patel", "Avery Chen"];
const tags = ["array", "hash-table", "dynamic-programming", "string", "graph", "binary-search"];

function randomIntBetween(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomItem(items) {
  return items[randomIntBetween(0, items.length - 1)];
}

function uuidv4() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (char) => {
    const value = Math.floor(Math.random() * 16);
    return (char === "x" ? value : (value & 0x3) | 0x8).toString(16);
  });
}

function authHeaders(accessToken, csrfToken) {
  return {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "x-csrf-token": csrfToken,
      "Content-Type": "application/json",
      "Cache-Control": "no-cache",
    },
  };
}

function getJson(res, fallback = {}) {
  try {
    return res.json();
  } catch (_) {
    return fallback;
  }
}

export default function () {
  const runId = uuidv4();
  const userNumber = String(randomIntBetween(1, 99)).padStart(3, "0");
  const email = `seeduser${userNumber}@example.com`;
  const problemPage = randomIntBetween(1, 40);
  const shouldTryAccepted = Math.random() < 0.72;

  let accessToken = "";
  let csrfToken = "";
  let userId = "";
  let problem = null;
  let submission = null;

  group("sign in and profile", () => {
    const loginRes = http.post(
      `${BASE_URL}/v1/auth/login`,
      JSON.stringify({ email, password: PASSWORD }),
      { headers: { "Content-Type": "application/json", "Cache-Control": "no-cache" } },
    );
    check(loginRes, { "login 200": (r) => r.status === 200 });
    const login = getJson(loginRes);
    accessToken = login.accessToken || "";
    csrfToken = login.csrfToken || "";

    const profileRes = http.get(`${BASE_URL}/v1/users/profile`, {
      headers: { Authorization: `Bearer ${accessToken}`, "Cache-Control": "no-cache" },
    });
    check(profileRes, { "profile 200": (r) => r.status === 200 });
    userId = getJson(profileRes)._id || "";

    if (userId) {
      const name = `${randomItem(names)} ${runId.slice(0, 6)}`;
      const avatar = `https://api.dicebear.com/9.x/initials/svg?seed=${encodeURIComponent(name)}`;
      const updateRes = http.patch(
        `${BASE_URL}/v1/users/${userId}`,
        JSON.stringify({ name, avatar }),
        authHeaders(accessToken, csrfToken),
      );
      check(updateRes, { "profile update 200": (r) => r.status === 200 });
    }
  });

  group("problem discovery", () => {
    const listRes = http.get(`${BASE_URL}/v1/problems?limit=1&page=${problemPage}`, {
      headers: { Authorization: `Bearer ${accessToken}`, "Cache-Control": "no-cache" },
    });
    check(listRes, { "problem list 200": (r) => r.status === 200 });
    problem = getJson(listRes).data?.[0];
    if (!problem?._id) return;

    check(http.get(`${BASE_URL}/v1/problems/${problem._id}`, {
      headers: { Authorization: `Bearer ${accessToken}`, "Cache-Control": "no-cache" },
    }), { "problem detail 200": (r) => r.status === 200 });

    check(http.get(`${BASE_URL}/v1/problems/${problem._id}/functions?language=${LANGUAGE}`, {
      headers: { "Cache-Control": "no-cache" },
    }), { "function declaration 200": (r) => r.status === 200 });
  });

  group("submission and branch", () => {
    if (!problem?._id) return;
    const fallbackName = (problem.title || "solution").split(/\s+/)[0].replace(/[^A-Za-z0-9_]/g, "") || "solution";
    const code = shouldTryAccepted && acceptedSolutions[problem.title]
      ? acceptedSolutions[problem.title]
      : `function ${fallbackName}() { return null; }`;

    const submitRes = http.post(
      `${BASE_URL}/v1/submissions`,
      JSON.stringify({ problemId: problem._id, language: LANGUAGE, code }),
      authHeaders(accessToken, csrfToken),
    );
    check(submitRes, { "submission 200": (r) => r.status === 200 });
    submission = getJson(submitRes);

    check(http.get(`${BASE_URL}/v1/submissions?problemId=${problem._id}&language=${LANGUAGE}&limit=5&page=1`, {
      headers: { Authorization: `Bearer ${accessToken}`, "Cache-Control": "no-cache" },
    }), { "submission history 200": (r) => r.status === 200 });

    if (submission?.status === "ACCEPTED") {
      const title = `Seed run ${runId.slice(0, 8)}: ${randomItem(tags)} solution notes`;
      const content = `Generated k6 solution note for ${problem.title} in run ${runId}.`;
      check(http.post(`${BASE_URL}/v1/discussions`, JSON.stringify({
        title,
        content,
        tags: ["solution", LANGUAGE, problem.difficulty || randomItem(tags)],
        solution: { problem: problem._id, language: LANGUAGE },
      }), authHeaders(accessToken, csrfToken)), { "solution discussion 201": (r) => r.status === 201 });

      check(http.get(`${BASE_URL}/v1/problems/${problem._id}/solutions?language=${LANGUAGE}&limit=5&page=1`, {
        headers: { "Cache-Control": "no-cache" },
      }), { "solutions 200": (r) => r.status === 200 });
    }
  });

  group("leaderboards and exit", () => {
    if (problem?._id) {
      check(http.get(`${BASE_URL}/v1/problems/${problem._id}/leaderboards?language=${LANGUAGE}&limit=10`, {
        headers: { Authorization: `Bearer ${accessToken}`, "Cache-Control": "no-cache" },
      }), { "leaderboard 200": (r) => r.status === 200 });

      http.post(`${BASE_URL}/v1/users/todos`, JSON.stringify({ problems: [problem._id] }), authHeaders(accessToken, csrfToken));
    }

    check(http.get(`${BASE_URL}/v1/problems/dailies?month=6&year=2026`, {
      headers: { "Cache-Control": "no-cache" },
    }), { "dailies 200": (r) => r.status === 200 });

    http.get(`${BASE_URL}/v1/auth/logout`, { headers: { "Cache-Control": "no-cache" } });
  });

  sleep(Math.random() * 2);
}
