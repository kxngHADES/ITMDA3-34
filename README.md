# ITMDA3-44

----

# How to start working

clone repo:

```bash
git clone https://github.com/kxngHADES/ITMDA3-34.git
```

change to repo:

```bash
cd ITMDA3-34
```

change to dev branch:

```bash
git switch dev
```

create a branch based on what you are working on in that moment:

```bash
git switch -c branch-name
```

> branch name is a placeholder
> Exmaple: feature/api-contracts

Upstream your repo before working:

```bash
git push -u origin branch-name
```

THEN YOU MAY START WORKING

WHEN DONE commit changes

Steps:

1. Add changes

```bash
git add .
```

2. commit and write a short message

```bash
git commit -m "Message in-between these the quotes"
```

3. Push changes

```bash
git push
```

## How to open a PR:

1. Go to github repo
2. Go to your branch
3. open a Pull Request
4. Target `dev`
5. fill in what is needed and remove what isnt from the template
6. ping a member to review the code and approve if the CI passes